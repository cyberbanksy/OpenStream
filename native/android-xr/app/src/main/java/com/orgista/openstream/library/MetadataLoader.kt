package com.orgista.openstream.library

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.TitleRatings
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
        val details = item.tmdbId?.let {
            if (item.kind == MediaKind.SERIES) tmdb.showDetails(it) else tmdb.movieDetails(it)
        }
        val imdbId = details?.imdbId ?: item.imdbId
        val omdbResult = imdbId?.let { runCatching { omdb.byImdbId(it) }.getOrNull() }

        val ratings = TitleRatings(
            imdb = omdbResult?.ratings?.imdb,
            rottenTomatoes = omdbResult?.ratings?.rottenTomatoes,
            metacritic = omdbResult?.ratings?.metacritic,
        ).takeIf { it.imdb != null || it.rottenTomatoes != null || it.metacritic != null }

        item.copy(
            imdbId = imdbId,
            rating = item.rating.ifBlank { details?.certification ?: omdbResult?.rated ?: "" },
            runtimeMinutes = if (item.runtimeMinutes > 0) item.runtimeMinutes
            else details?.runtimeMinutes ?: omdbResult?.runtimeMinutes ?: 0,
            genres = details?.genres?.ifEmpty { omdbResult?.genres ?: emptyList() }
                ?: omdbResult?.genres ?: emptyList(),
            cast = details?.cast?.ifEmpty { omdbResult?.actors ?: emptyList() }
                ?: omdbResult?.actors ?: emptyList(),
            director = details?.director ?: omdbResult?.director,
            tagline = details?.tagline,
            ratings = ratings,
        )
    }
}
