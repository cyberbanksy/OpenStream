package com.orgista.openstream.data

import android.content.Context
import com.orgista.openstream.Logs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedStremioCatalog(val updatedAtMs: Long, val items: List<MediaItem>) {
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        items.isNotEmpty() && nowMs - updatedAtMs in 0 until REFRESH_AFTER_MS

    private companion object {
        const val REFRESH_AFTER_MS = 6L * 60L * 60L * 1_000L
    }
}

/** Offline fallback for public discovery metadata; no add-on URL is cached. */
object StremioCatalogCache {
    private fun file(context: Context) = File(context.filesDir, "stremio-catalog-v1.json")

    fun load(context: Context): CachedStremioCatalog? {
        val source = file(context)
        if (!source.exists()) return null
        return runCatching {
            val root = JSONObject(source.readText())
            val values = root.optJSONArray("items") ?: JSONArray()
            val items = (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.toItem()
            }
            CachedStremioCatalog(root.optLong("updatedAtMs"), items)
        }.onFailure { Logs.warn("add-on catalog cache could not be read") }.getOrNull()
    }

    fun save(context: Context, items: List<MediaItem>, nowMs: Long = System.currentTimeMillis()) {
        if (items.isEmpty()) return
        runCatching {
            val root = JSONObject()
                .put("updatedAtMs", nowMs)
                .put("items", JSONArray(items.map { it.toJson() }))
            val target = file(context)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(target)) {
                target.writeText(root.toString())
                temporary.delete()
            }
        }.onFailure { Logs.warn("add-on catalog cache could not be saved") }
    }

    private fun MediaItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("title", title)
        .putOpt("subtitle", subtitle)
        .put("synopsis", synopsis)
        .put("year", year)
        .put("rating", rating)
        .put("runtimeMinutes", runtimeMinutes)
        .putOpt("posterUrl", posterUrl)
        .putOpt("backdropUrl", backdropUrl)
        .putOpt("imdbId", imdbId)
        .put("genres", JSONArray(genres))
        .put("cast", JSONArray(cast))
        .putOpt("director", director)
        .putOpt("ratings", ratings?.let { JSONObject().putOpt("imdb", it.imdb) })

    private fun JSONObject.toItem(): MediaItem? {
        val id = optString("id")
        val title = optString("title")
        if (id.isBlank() || title.isBlank()) return null
        return MediaItem(
            id = id,
            kind = runCatching { MediaKind.valueOf(optString("kind")) }.getOrDefault(MediaKind.MOVIE),
            title = title,
            subtitle = optString("subtitle").takeIf(String::isNotBlank),
            synopsis = optString("synopsis"),
            year = optInt("year"),
            rating = optString("rating"),
            runtimeMinutes = optInt("runtimeMinutes"),
            posterUrl = optString("posterUrl").takeIf(String::isNotBlank),
            backdropUrl = optString("backdropUrl").takeIf(String::isNotBlank),
            imdbId = optString("imdbId").takeIf(String::isNotBlank),
            genres = optJSONArray("genres").strings(),
            cast = optJSONArray("cast").strings(),
            director = optString("director").trim().takeIf {
                it.isNotBlank() && it != "[]" && !it.equals("null", ignoreCase = true)
            },
            ratings = optJSONObject("ratings")?.optString("imdb")?.takeIf(String::isNotBlank)
                ?.let { TitleRatings(imdb = it) },
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList()
        else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
