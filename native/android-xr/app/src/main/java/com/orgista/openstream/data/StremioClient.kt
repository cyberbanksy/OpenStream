package com.orgista.openstream.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class StremioStream(val title: String, val url: String)

/**
 * Minimal Stremio add-on client: validates a manifest and resolves direct
 * HTTP(S) streams for a title. Torrent/external locators are ignored — only
 * streams the player can open directly are returned.
 */
object StremioClient {
    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Add-on responded with HTTP ${connection.responseCode}.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.length > 2_000_000) throw IllegalStateException("Add-on response is too large.")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Fetches and validates a manifest; returns the addon ready to store. */
    fun loadAddon(value: String): StremioAddon {
        val trimmed = value.trim().removeSuffix("/")
        if (!trimmed.startsWith("https://")) throw IllegalArgumentException("Add-on URLs must use HTTPS.")
        val manifestUrl = if (trimmed.endsWith("manifest.json")) trimmed else "$trimmed/manifest.json"
        val manifest = getJson(manifestUrl)
        val name = manifest.optString("name")
        if (manifest.optString("id").isEmpty() || name.isEmpty()) {
            throw IllegalStateException("The manifest is missing its id or name.")
        }
        return StremioAddon(
            id = UUID.randomUUID().toString(),
            name = name,
            transportUrl = manifestUrl.removeSuffix("/manifest.json"),
            version = manifest.optString("version"),
        )
    }

    /** Resolves directly playable streams for an IMDb title id. */
    fun streamsFor(addon: StremioAddon, type: String, imdbId: String): List<StremioStream> {
        val payload = getJson("${addon.transportUrl}/stream/$type/$imdbId.json")
        val streams = payload.optJSONArray("streams") ?: return emptyList()
        return (0 until streams.length()).mapNotNull { index ->
            val stream = streams.optJSONObject(index) ?: return@mapNotNull null
            val url = stream.optString("url")
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
            StremioStream(
                title = stream.optString("title", stream.optString("name", "Stream ${index + 1}")).take(120),
                url = url,
            )
        }.take(50)
    }
}
