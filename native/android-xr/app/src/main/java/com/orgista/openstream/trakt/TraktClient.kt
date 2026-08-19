package com.orgista.openstream.trakt

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import org.json.JSONObject

data class TraktDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

enum class TraktScrobbleAction(val path: String) { START("start"), PAUSE("pause"), STOP("stop") }

class TraktClient(private val store: TraktStore) {
    val connected: Boolean get() = store.tokens != null

    fun beginDeviceAuthorization(): TraktDeviceAuthorization {
        val credentials = requireCredentials()
        val response = request(
            "https://auth.trakt.tv/oauth/device/code",
            "POST",
            JSONObject().put("client_id", credentials.clientId),
            authenticated = false,
        )
        if (response.code !in 200..299) throw IOException("Trakt could not begin sign-in (HTTP ${response.code}).")
        return TraktDeviceAuthorization(
            deviceCode = response.json.getString("device_code"),
            userCode = response.json.getString("user_code"),
            verificationUrl = response.json.optString("verification_url", "https://auth.trakt.tv/activate"),
            expiresInSeconds = response.json.optInt("expires_in", 600),
            intervalSeconds = response.json.optInt("interval", 5).coerceAtLeast(1),
        )
    }

    suspend fun awaitDeviceAuthorization(authorization: TraktDeviceAuthorization): TraktTokens {
        val credentials = requireCredentials()
        val deadline = System.currentTimeMillis() + authorization.expiresInSeconds * 1_000L
        while (System.currentTimeMillis() < deadline) {
            val response = request(
                "https://auth.trakt.tv/oauth/device/token",
                "POST",
                JSONObject()
                    .put("code", authorization.deviceCode)
                    .put("client_id", credentials.clientId)
                    .put("client_secret", credentials.clientSecret),
                authenticated = false,
            )
            when (response.code) {
                in 200..299 -> return parseAndSaveTokens(response.json)
                400 -> delay(authorization.intervalSeconds * 1_000L)
                404 -> throw IOException("Trakt sign-in code was not found.")
                409 -> throw IOException("This Trakt code was already used.")
                410 -> throw IOException("The Trakt sign-in code expired.")
                418 -> throw IOException("Trakt sign-in was declined.")
                429 -> delay((authorization.intervalSeconds + 5) * 1_000L)
                else -> throw IOException("Trakt sign-in failed (HTTP ${response.code}).")
            }
        }
        throw IOException("The Trakt sign-in code expired.")
    }

    fun scrobble(action: TraktScrobbleAction, identity: TraktMediaIdentity, progress: Double) {
        val token = validAccessToken()
        val response = request(
            "https://api.trakt.tv/scrobble/${action.path}",
            "POST",
            TraktPayloads.scrobble(identity, progress),
            accessToken = token,
        )
        if (response.code !in 200..299) throw IOException("Trakt playback sync failed (HTTP ${response.code}).")
    }

    fun resumePercent(identity: TraktMediaIdentity): Double? {
        val token = validAccessToken()
        val path = if (identity.type == TraktMediaType.MOVIE) "movies" else "episodes"
        val response = request("https://api.trakt.tv/sync/playback/$path", "GET", accessToken = token)
        if (response.code !in 200..299) return null
        val array = runCatching { org.json.JSONArray(response.raw) }.getOrNull() ?: return null
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val container = if (identity.type == TraktMediaType.MOVIE) entry.optJSONObject("movie") else entry.optJSONObject("show")
            if (container?.optJSONObject("ids")?.optString("imdb") == identity.imdbId) {
                if (identity.type == TraktMediaType.EPISODE) {
                    val episode = entry.optJSONObject("episode") ?: continue
                    if (episode.optInt("season") != identity.season || episode.optInt("number") != identity.episode) continue
                }
                return entry.optDouble("progress").takeIf { it in 0.1..99.9 }
            }
        }
        return null
    }

    @Synchronized
    private fun validAccessToken(): String {
        val tokens = store.tokens ?: throw IOException("Connect Trakt in Settings first.")
        if (tokens.expiresAtMillis > System.currentTimeMillis() + 60_000L) return tokens.accessToken
        val credentials = requireCredentials()
        if (tokens.refreshToken.isBlank()) throw IOException("Trakt sign-in expired. Reconnect in Settings.")
        val response = request(
            "https://api.trakt.tv/oauth/token",
            "POST",
            JSONObject()
                .put("refresh_token", tokens.refreshToken)
                .put("client_id", credentials.clientId)
                .put("client_secret", credentials.clientSecret)
                .put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
                .put("grant_type", "refresh_token"),
            authenticated = false,
        )
        if (response.code !in 200..299) throw IOException("Trakt sign-in expired. Reconnect in Settings.")
        return parseAndSaveTokens(response.json).accessToken
    }

    private fun parseAndSaveTokens(json: JSONObject): TraktTokens {
        val createdAt = json.optLong("created_at", System.currentTimeMillis() / 1_000L)
        val expiresIn = json.optLong("expires_in", 7L * 24L * 60L * 60L)
        val tokens = TraktTokens(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token"),
            expiresAtMillis = (createdAt + expiresIn) * 1_000L,
        )
        store.saveTokens(tokens)
        return tokens
    }

    private fun requireCredentials(): TraktAppCredentials = store.credentials
        ?: throw IOException("Enter a Trakt application client ID and secret first.")

    private data class Response(val code: Int, val raw: String) {
        val json: JSONObject get() = if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun request(
        url: String,
        method: String,
        payload: JSONObject? = null,
        authenticated: Boolean = true,
        accessToken: String? = null,
    ): Response {
        val credentials = store.credentials
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("trakt-api-version", "2")
        credentials?.clientId?.let { connection.setRequestProperty("trakt-api-key", it) }
        if (authenticated) connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: validAccessToken()}")
        try {
            if (payload != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText().take(1_000_000) }.orEmpty()
            return Response(code, raw)
        } finally {
            connection.disconnect()
        }
    }
}
