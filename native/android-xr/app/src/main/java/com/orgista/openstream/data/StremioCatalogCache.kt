package com.orgista.openstream.data

import android.content.Context
import com.orgista.openstream.Logs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedStremioCatalog(
    val updatedAtMs: Long,
    val items: List<MediaItem>,
    val sections: List<AddonCatalogSection> = emptyList(),
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        items.isNotEmpty() && nowMs - updatedAtMs in 0 until REFRESH_AFTER_MS

    companion object {
        private const val REFRESH_AFTER_MS = 6L * 60L * 60L * 1_000L
    }
}

/** Offline fallback for user-configured discovery metadata; no add-on URL is cached. */
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
            val sectionValues = root.optJSONArray("sections") ?: JSONArray()
            val sections = (0 until sectionValues.length()).mapNotNull { index ->
                sectionValues.optJSONObject(index)?.toSection()
            }
            CachedStremioCatalog(root.optLong("updatedAtMs"), items, sections)
        }.onFailure { Logs.warn("add-on catalog cache could not be read") }.getOrNull()
    }

    fun remove(context: Context) {
        file(context).delete()
    }

    fun saveSections(
        context: Context,
        sections: List<AddonCatalogSection>,
        items: List<MediaItem>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        save(context, items, sections, nowMs)
    }

    private fun save(
        context: Context,
        items: List<MediaItem>,
        sections: List<AddonCatalogSection>,
        nowMs: Long,
    ) {
        if (items.isEmpty()) return
        runCatching {
            val root = JSONObject()
                .put("updatedAtMs", nowMs)
                .put("items", JSONArray(items.map { it.toJson() }))
                .put("sections", JSONArray(sections.map { it.toJson() }))
            val target = file(context)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(target)) {
                target.writeText(root.toString())
                temporary.delete()
            }
        }.onFailure { Logs.warn("add-on catalog cache could not be saved") }
    }

    private fun AddonCatalogSection.toJson(): JSONObject = JSONObject()
        .put("addonId", addonId)
        .put("addonName", addonName)
        .put("catalogId", catalogId)
        .put("catalogName", catalogName)
        .put("items", JSONArray(items.map { it.toJson() }))

    private fun JSONObject.toSection(): AddonCatalogSection? {
        val addonId = optString("addonId")
        val addonName = optString("addonName")
        val catalogId = optString("catalogId")
        val catalogName = optString("catalogName")
        if (addonId.isBlank() || addonName.isBlank() || catalogId.isBlank() || catalogName.isBlank()) return null
        val values = optJSONArray("items") ?: JSONArray()
        val items = (0 until values.length()).mapNotNull { values.optJSONObject(it)?.toItem() }
        return AddonCatalogSection(addonId, addonName, catalogId, catalogName, items)
            .takeIf { it.items.isNotEmpty() }
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
        .putOpt("trailerYoutubeKey", trailerYoutubeKey)
        .putOpt("imdbId", imdbId)
        .putOpt("tmdbId", tmdbId)
        .put("genres", JSONArray(genres))
        .put("cast", JSONArray(cast))
        .putOpt("director", director)
        .putOpt("tagline", tagline)
        .putOpt("ratings", ratings?.let {
            JSONObject()
                .putOpt("imdb", it.imdb)
                .putOpt("rottenTomatoes", it.rottenTomatoes)
                .putOpt("metacritic", it.metacritic)
        })
        .put("episodes", JSONArray(episodes.map { it.toJson() }))

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
            trailerYoutubeKey = optString("trailerYoutubeKey").takeIf(String::isNotBlank),
            imdbId = optString("imdbId").takeIf(String::isNotBlank),
            tmdbId = if (has("tmdbId") && !isNull("tmdbId")) optInt("tmdbId") else null,
            genres = optJSONArray("genres").strings(),
            cast = optJSONArray("cast").strings(),
            director = optString("director").trim().takeIf {
                it.isNotBlank() && it != "[]" && !it.equals("null", ignoreCase = true)
            },
            tagline = optString("tagline").takeIf(String::isNotBlank),
            ratings = optJSONObject("ratings")?.let { ratings ->
                TitleRatings(
                    imdb = ratings.optString("imdb").takeIf(String::isNotBlank),
                    rottenTomatoes = ratings.optString("rottenTomatoes").takeIf(String::isNotBlank),
                    metacritic = ratings.optString("metacritic").takeIf(String::isNotBlank),
                ).takeIf { it.imdb != null || it.rottenTomatoes != null || it.metacritic != null }
            },
            episodes = optJSONArray("episodes")?.let { values ->
                (0 until values.length()).mapNotNull { values.optJSONObject(it)?.toItem() }
            }.orEmpty(),
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList()
        else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}

/** Retains cache rows only when their add-on and video catalog still exist in user settings. */
object ConfiguredStremioCatalogPolicy {
    fun retain(cached: CachedStremioCatalog?, configured: List<StremioAddon>): CachedStremioCatalog? {
        if (cached == null) return null
        val allowed = configured
            .filter { it.enabled }
            .associate { addon ->
                addon.manifestId to addon.catalogs
                    .filter { StremioMediaTypePolicy.isSupported(it.type) }
                    .mapTo(hashSetOf()) { "${it.type}:${it.id}" }
            }
        val sections = cached.sections.filter { section ->
            section.catalogId in allowed[section.addonId].orEmpty()
        }
        val items = sections.flatMap(AddonCatalogSection::items).distinctBy { it.imdbId ?: it.id }
        return cached.copy(items = items, sections = sections).takeIf { sections.isNotEmpty() }
    }
}
