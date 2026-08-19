package com.orgista.openstream.library

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills in full metadata for one title on demand — invoked when its Details
 * screen opens, so the library scan stays fast. Pulls genres/cast/director/
 * certification from TMDB and Rotten Tomatoes/Metacritic/IMDb scores from
 * OMDb, then returns an enriched copy of the item.
 */
class MetadataLoader(tmdbKey: String, omdbKey: String) {
    private val tmdb = Tmdb(tmdbKey)
    private val omdb = Omdb(omdbKey)

    val active: Boolean get() = tmdb.enabled || omdb.enabled

    suspend fun enrich(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val suppliedImdbId = item.imdbId ?: item.id.takeIf { it.matches(Regex("tt\\d{5,12}")) }
        val exactMatch = suppliedImdbId?.let { id ->
            runCatching { tmdb.findByImdbId(id, item.kind) }.getOrNull()
        }
        val resolvedTmdbId = item.tmdbId ?: exactMatch?.tmdbId ?: runCatching {
            if (item.kind == MediaKind.SERIES) tmdb.searchShow(item.title)?.tmdbId
            else tmdb.searchMovie(item.title, item.year.takeIf { it > 0 })?.tmdbId
        }.getOrNull()
        val details = resolvedTmdbId?.let {
            if (item.kind == MediaKind.SERIES) tmdb.showDetails(it) else tmdb.movieDetails(it)
        }
        val imdbId = details?.imdbId ?: suppliedImdbId
        val omdbResult = imdbId?.let { runCatching { omdb.byImdbId(it) }.getOrNull() }
        MetadataMergePolicy.merge(item, resolvedTmdbId, details, omdbResult)
    }
}
