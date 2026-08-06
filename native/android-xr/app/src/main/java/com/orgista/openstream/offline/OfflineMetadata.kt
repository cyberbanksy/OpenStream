package com.orgista.openstream.offline

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.SourceKind
import com.orgista.openstream.data.TitleRatings
import org.json.JSONArray
import org.json.JSONObject

internal object OfflineMetadata {
    fun encode(item: MediaItem, descriptor: OfflineStreamDescriptor): ByteArray = JSONObject()
        .put("version", 1)
        .put("id", item.id)
        .put("kind", item.kind.name)
        .put("title", item.title)
        .put("subtitle", item.subtitle.orEmpty())
        .put("synopsis", item.synopsis)
        .put("year", item.year)
        .put("rating", item.rating)
        .put("runtimeMinutes", item.runtimeMinutes)
        .put("posterUrl", item.posterUrl.orEmpty())
        .put("backdropUrl", item.backdropUrl.orEmpty())
        .put("imdbId", item.imdbId.orEmpty())
        .put("tmdbId", item.tmdbId ?: 0)
        .put("genres", JSONArray(item.genres))
        .put("cast", JSONArray(item.cast))
        .put("director", item.director.orEmpty())
        .put("tagline", item.tagline.orEmpty())
        .put("imdbRating", item.ratings?.imdb.orEmpty())
        .put("rottenTomatoes", item.ratings?.rottenTomatoes.orEmpty())
        .put("metacritic", item.ratings?.metacritic.orEmpty())
        .put("qualityLabel", descriptor.qualityLabel)
        .toString()
        .toByteArray(Charsets.UTF_8)

    @UnstableApi
    fun decode(download: Download): MediaItem? = runCatching {
        val json = JSONObject(download.request.data.toString(Charsets.UTF_8))
        val originalId = json.getString("id")
        fun optional(key: String) = json.optString(key).trim().takeIf(String::isNotBlank)
        fun strings(key: String): List<String> {
            val values = json.optJSONArray(key) ?: return emptyList()
            return (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
        }
        val ratings = TitleRatings(
            imdb = optional("imdbRating"),
            rottenTomatoes = optional("rottenTomatoes"),
            metacritic = optional("metacritic"),
        ).takeIf { it.imdb != null || it.rottenTomatoes != null || it.metacritic != null }
        MediaItem(
            id = originalId,
            kind = runCatching { MediaKind.valueOf(json.getString("kind")) }.getOrDefault(MediaKind.VIDEO),
            title = json.getString("title"),
            subtitle = "Available offline",
            synopsis = json.optString("synopsis"),
            year = json.optInt("year"),
            rating = json.optString("rating"),
            runtimeMinutes = json.optInt("runtimeMinutes"),
            posterUrl = optional("posterUrl"),
            backdropUrl = optional("backdropUrl"),
            imdbId = optional("imdbId"),
            tmdbId = json.optInt("tmdbId").takeIf { it > 0 },
            candidates = listOf(
                MediaCandidate(
                    id = download.request.id,
                    sourceName = "On this device",
                    sourceKind = SourceKind.OFFLINE,
                    uri = download.request.uri.toString(),
                    formatLabel = json.optString("qualityLabel", "Downloaded"),
                    offlineDownloadId = download.request.id,
                ),
            ),
            genres = strings("genres"),
            cast = strings("cast"),
            director = optional("director"),
            tagline = optional("tagline"),
            ratings = ratings,
        )
    }.getOrNull()
}
