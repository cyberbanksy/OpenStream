package com.orgista.openstream.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class StremioStream(val title: String, val url: String)

object StremioRetryPolicy {
    private const val MAX_RETRIES = 3

    fun shouldRetry(httpCode: Int, attempt: Int): Boolean =
        attempt < MAX_RETRIES && (httpCode == 408 || httpCode == 429 || httpCode in 500..599)

    fun shouldRetry(error: Throwable, attempt: Int): Boolean = attempt < MAX_RETRIES && error is IOException

    fun delayMs(attempt: Int): Long = 250L * (1L shl attempt.coerceIn(0, MAX_RETRIES))
}

object StremioUrlPolicy {
    fun safeLabel(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: return "Configured add-on"
        val host = uri.host?.takeIf(String::isNotBlank) ?: return "Configured add-on"
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
        val configured = segments.any { it != "manifest.json" }
        return if (configured) "$host · private configuration" else host
    }

    fun requireHttps(value: String): String {
        val trimmed = value.trim().removeSuffix("/")
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid add-on URL.")
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
            throw IllegalArgumentException("Add-on URLs must use HTTPS.")
        }
        return trimmed
    }
}

private class AddonHttpException(val status: Int) : IOException("Add-on responded with HTTP $status.")

/**
 * Stremio protocol client for manifest, catalog, meta, and directly playable
 * HTTPS stream resources. Torrent locators are intentionally ignored: the app
 * only hands Media3 a URL it can open itself.
 */
object StremioClient {
    private const val MAX_RESPONSE_BYTES = 2_000_000

    private fun getJson(url: String): JSONObject {
        var lastFailure: Throwable? = null
        for (attempt in 0..3) {
            try {
                return getJsonOnce(url)
            } catch (failure: Throwable) {
                lastFailure = failure
                val retry = when (failure) {
                    is AddonHttpException -> StremioRetryPolicy.shouldRetry(failure.status, attempt)
                    else -> StremioRetryPolicy.shouldRetry(failure, attempt)
                }
                if (!retry) throw failure
                Thread.sleep(StremioRetryPolicy.delayMs(attempt))
            }
        }
        throw lastFailure ?: IllegalStateException("The add-on did not respond.")
    }

    private fun getJsonOnce(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "OpenStream/0.1")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw AddonHttpException(code)
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("Add-on response is too large.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return JSONObject(String(bytes, StandardCharsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    /** Fetches and validates a manifest; returns the add-on ready to store. */
    fun loadAddon(value: String): StremioAddon {
        val trimmed = StremioUrlPolicy.requireHttps(value)
        val manifestUrl = if (trimmed.endsWith("manifest.json")) trimmed else "$trimmed/manifest.json"
        return parseManifest(manifestUrl, getJson(manifestUrl).toString())
    }

    fun parseManifest(manifestUrl: String, rawJson: String): StremioAddon {
        val safeUrl = StremioUrlPolicy.requireHttps(manifestUrl)
        val manifest = JSONObject(rawJson)
        val manifestId = manifest.optString("id")
        val name = manifest.optString("name")
        if (manifestId.isBlank() || name.isBlank()) {
            throw IllegalStateException("The manifest is missing its id or name.")
        }
        val resources = parseResources(manifest.optJSONArray("resources"))
        val catalogs = parseCatalogDescriptors(manifest.optJSONArray("catalogs"))
        return StremioAddon(
            id = UUID.randomUUID().toString(),
            manifestId = manifestId,
            name = name.take(100),
            transportUrl = safeUrl.removeSuffix("/manifest.json"),
            version = manifest.optString("version").take(40),
            resources = resources,
            catalogs = catalogs,
        )
    }

    private fun parseResources(values: JSONArray?): Set<String> {
        if (values == null) return emptySet()
        return (0 until values.length()).mapNotNull { index ->
            when (val value = values.opt(index)) {
                is String -> value
                is JSONObject -> value.optString("name")
                else -> null
            }?.trim()?.takeIf(String::isNotBlank)
        }.toSet()
    }

    private fun parseCatalogDescriptors(values: JSONArray?): List<StremioCatalog> {
        if (values == null) return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val type = value.optString("type")
            val id = value.optString("id")
            if (type.isBlank() || id.isBlank()) return@mapNotNull null
            val extra = value.optJSONArray("extra")
            val requiresInput = extra != null && (0 until extra.length()).any { extraIndex ->
                extra.optJSONObject(extraIndex)?.optBoolean("isRequired") == true
            }
            StremioCatalog(type, id, value.optString("name", id).take(100), requiresInput)
        }
    }

    fun catalogsFor(addon: StremioAddon, maximumCatalogs: Int = 2): List<MediaItem> {
        if (!addon.enabled || "catalog" !in addon.resources) return emptyList()
        return addon.catalogs
            .filterNot { it.requiresInput }
            .take(maximumCatalogs.coerceIn(0, 4))
            .flatMap { catalog ->
                runCatching {
                    val type = pathSegment(catalog.type)
                    val id = pathSegment(catalog.id)
                    parseCatalog(addon, getJson("${addon.transportUrl}/catalog/$type/$id.json").toString())
                }.getOrDefault(emptyList())
            }
            .distinctBy { it.imdbId ?: it.id }
    }

    fun parseCatalog(addon: StremioAddon, rawJson: String): List<MediaItem> {
        val metas = JSONObject(rawJson).optJSONArray("metas") ?: return emptyList()
        return (0 until metas.length()).mapNotNull { index ->
            metas.optJSONObject(index)?.let { parseMetaObject(addon, it) }
        }.take(100)
    }

    fun metaFor(addon: StremioAddon, type: String, id: String): MediaItem? {
        if (!addon.enabled || "meta" !in addon.resources) return null
        val payload = getJson("${addon.transportUrl}/meta/${pathSegment(type)}/${pathSegment(id)}.json")
        return payload.optJSONObject("meta")?.let { parseMetaObject(addon, it) }
    }

    private fun parseMetaObject(addon: StremioAddon, meta: JSONObject): MediaItem? {
        val rawId = meta.optString("id")
        val title = meta.optString("name", meta.optString("title"))
        if (rawId.isBlank() || title.isBlank()) return null
        val type = meta.optString("type", "movie")
        val year = Regex("(?:19|20)\\d{2}").find(meta.optString("releaseInfo", meta.optString("released")))
            ?.value?.toIntOrNull() ?: meta.optInt("year", 0)
        val genres = meta.optJSONArray("genres")?.let { values ->
            (0 until values.length()).mapNotNull { values.optString(it).trim().takeIf(String::isNotBlank) }
        }.orEmpty()
        val imdbId = listOf(meta.optString("imdbId"), meta.optString("imdb_id"), rawId)
            .firstOrNull { it.matches(Regex("tt\\d{5,12}")) }
        val imdbRating = meta.opt("imdbRating")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        return MediaItem(
            id = imdbId ?: "stremio:${addon.manifestId}:$rawId",
            kind = if (type == "series" || type == "tv") MediaKind.SERIES else MediaKind.MOVIE,
            title = title.take(180),
            subtitle = addon.name,
            synopsis = meta.optString("description").take(4_000),
            year = year,
            rating = meta.optString("contentRating", meta.optString("certification")),
            runtimeMinutes = parseRuntime(meta.optString("runtime")),
            posterUrl = safeImage(meta.optString("poster")),
            backdropUrl = safeImage(meta.optString("background", meta.optString("backdrop"))),
            imdbId = imdbId,
            genres = genres.take(8),
            cast = jsonStrings(meta.optJSONArray("cast")).take(12),
            director = firstPerson(meta.opt("director")),
            ratings = imdbRating?.let { TitleRatings(imdb = it) },
        )
    }

    /** Resolves directly playable streams for an IMDb title id. */
    fun streamsFor(addon: StremioAddon, type: String, imdbId: String): List<StremioStream> {
        if (!addon.enabled || "stream" !in addon.resources) return emptyList()
        val payload = getJson("${addon.transportUrl}/stream/${pathSegment(type)}/${pathSegment(imdbId)}.json")
        val streams = payload.optJSONArray("streams") ?: return emptyList()
        return (0 until streams.length()).mapNotNull { index ->
            val stream = streams.optJSONObject(index) ?: return@mapNotNull null
            val url = stream.optString("url")
            if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
            StremioStream(
                title = stream.optString("title", stream.optString("name", "Stream ${index + 1}"))
                    .replace(Regex("[\\r\\n\\t]+"), " ")
                    .take(120),
                url = url,
            )
        }.distinctBy { it.url }.take(50)
    }

    private fun pathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun parseRuntime(value: String): Int = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: 0

    private fun safeImage(value: String): String? = value.takeIf {
        it.startsWith("https://") || it.startsWith("http://")
    }

    private fun jsonStrings(values: JSONArray?): List<String> =
        if (values == null) emptyList()
        else (0 until values.length()).mapNotNull { values.optString(it).trim().takeIf(String::isNotBlank) }

    private fun firstPerson(value: Any?): String? = when (value) {
        is JSONArray -> jsonStrings(value).firstOrNull()
        is String -> {
            val trimmed = value.trim()
            when {
                trimmed.isBlank() || trimmed == "[]" || trimmed.equals("null", ignoreCase = true) -> null
                trimmed.startsWith("[") -> runCatching { jsonStrings(JSONArray(trimmed)).firstOrNull() }.getOrNull()
                else -> trimmed.substringBefore(',').trim().takeIf(String::isNotBlank)
            }
        }
        else -> null
    }
}

/** Merges remote discovery metadata without replacing a local playable route. */
object StremioCatalogMerger {
    fun merge(local: List<MediaItem>, remote: List<MediaItem>): List<MediaItem> {
        val result = LinkedHashMap<String, MediaItem>()
        (local + remote).forEach { incoming ->
            val key = incoming.imdbId ?: incoming.id
            val existing = result[key]
            result[key] = if (existing == null) incoming else mergeItem(existing, incoming)
        }
        return result.values.toList()
    }

    fun mergeItem(preferred: MediaItem, extra: MediaItem): MediaItem = preferred.copy(
        synopsis = preferred.synopsis.ifBlank { extra.synopsis },
        year = preferred.year.takeIf { it > 0 } ?: extra.year,
        rating = preferred.rating.ifBlank { extra.rating },
        runtimeMinutes = preferred.runtimeMinutes.takeIf { it > 0 } ?: extra.runtimeMinutes,
        posterUrl = preferred.posterUrl ?: extra.posterUrl,
        backdropUrl = preferred.backdropUrl ?: extra.backdropUrl,
        imdbId = preferred.imdbId ?: extra.imdbId,
        tmdbId = preferred.tmdbId ?: extra.tmdbId,
        genres = (preferred.genres + extra.genres).distinctBy(String::lowercase),
        cast = (preferred.cast + extra.cast).distinct().take(12),
        director = preferred.director.validPerson() ?: extra.director.validPerson(),
        tagline = preferred.tagline ?: extra.tagline,
        ratings = preferred.ratings ?: extra.ratings,
    )

    private fun String?.validPerson(): String? = this
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "[]" && !it.equals("null", ignoreCase = true) }
}
