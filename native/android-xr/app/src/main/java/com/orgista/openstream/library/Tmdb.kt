package com.orgista.openstream.library

import com.orgista.openstream.Logs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class TmdbResult(
    val title: String,
    val overview: String,
    val year: Int?,
    val rating: String,
    val posterPath: String?,
    val backdropPath: String?,
    val tmdbId: Int,
)

/** Full detail for a title, fetched lazily when its Details screen opens. */
data class TmdbDetails(
    val imdbId: String?,
    val runtimeMinutes: Int,
    val genres: List<String>,
    val cast: List<String>,
    val director: String?,
    val tagline: String?,
    val certification: String?,
)

/**
 * TMDB metadata client. A user-supplied v3 API key (Settings) unlocks posters,
 * backdrops, overviews, and certifications — the same source Infuse and
 * Jellyfin scrape. Without a key the library still organizes offline from
 * parsed filenames; this simply enriches it.
 */
class Tmdb(private val apiKey: String) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    fun searchMovie(title: String, year: Int?): TmdbResult? {
        if (!enabled) return null
        val url = buildString {
            append("https://api.themoviedb.org/3/search/movie?api_key=$apiKey&query=")
            append(URLEncoder.encode(title, "UTF-8"))
            if (year != null) append("&year=$year")
        }
        val first = firstResult(url) ?: return null
        return toResult(first, isMovie = true)
    }

    fun searchShow(title: String): TmdbResult? {
        if (!enabled) return null
        val url = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=" +
            URLEncoder.encode(title, "UTF-8")
        val first = firstResult(url) ?: return null
        return toResult(first, isMovie = false)
    }

    /** Movie detail with credits, IMDb id, and US certification in one call. */
    fun movieDetails(tmdbId: Int): TmdbDetails? {
        if (!enabled) return null
        val json = getJson(
            "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$apiKey" +
                "&append_to_response=credits,release_dates",
        ) ?: return null
        val cert = usCertification(json.optJSONObject("release_dates"), "movie")
        return toDetails(json, json.optString("imdb_id").takeIf { it.isNotBlank() }, cert)
    }

    fun showDetails(tmdbId: Int): TmdbDetails? {
        if (!enabled) return null
        val json = getJson(
            "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey" +
                "&append_to_response=credits,external_ids,content_ratings",
        ) ?: return null
        val imdb = json.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() }
        val cert = usCertification(json.optJSONObject("content_ratings"), "tv")
        return toDetails(json, imdb, cert)
    }

    private fun toDetails(json: JSONObject, imdbId: String?, cert: String?): TmdbDetails {
        val genres = json.optJSONArray("genres")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
        } ?: emptyList()
        val credits = json.optJSONObject("credits")
        val castArray = credits?.optJSONArray("cast")
        val cast = castArray?.let { array ->
            (0 until minOf(array.length(), 10)).mapNotNull { array.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
        } ?: emptyList()
        val director = credits?.optJSONArray("crew")?.let { array ->
            (0 until array.length()).map { array.optJSONObject(it) }
                .firstOrNull { it?.optString("job") == "Director" }?.optString("name")
        }?.takeIf { it.isNotBlank() }
        val runtime = json.optInt("runtime", 0).takeIf { it > 0 }
            ?: json.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0
        return TmdbDetails(
            imdbId = imdbId,
            runtimeMinutes = runtime,
            genres = genres,
            cast = cast,
            director = director,
            tagline = json.optString("tagline").takeIf { it.isNotBlank() },
            certification = cert,
        )
    }

    private fun usCertification(container: JSONObject?, type: String): String? {
        val results = container?.optJSONArray("results") ?: return null
        for (index in 0 until results.length()) {
            val entry = results.optJSONObject(index) ?: continue
            val country = entry.optString("iso_3166_1")
            if (country != "US") continue
            return if (type == "movie") {
                val releases = entry.optJSONArray("release_dates") ?: continue
                (0 until releases.length())
                    .mapNotNull { releases.optJSONObject(it)?.optString("certification") }
                    .firstOrNull { it.isNotBlank() }
            } else {
                entry.optString("rating").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun firstResult(url: String): JSONObject? {
        val payload = getJson(url) ?: return null
        val results = payload.optJSONArray("results") ?: return null
        return if (results.length() > 0) results.optJSONObject(0) else null
    }

    private fun toResult(json: JSONObject, isMovie: Boolean): TmdbResult {
        val date = json.optString(if (isMovie) "release_date" else "first_air_date")
        val year = date.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        val voteAverage = json.optDouble("vote_average", 0.0)
        return TmdbResult(
            title = json.optString(if (isMovie) "title" else "name").ifBlank { json.optString("original_title") },
            overview = json.optString("overview"),
            year = year,
            rating = if (voteAverage > 0) "★ ${"%.1f".format(voteAverage)}" else "",
            posterPath = json.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
            backdropPath = json.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
            tmdbId = json.optInt("id"),
        )
    }

    private fun getJson(url: String): JSONObject? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode !in 200..299) {
                    Logs.warn("tmdb HTTP ${connection.responseCode}")
                    return null
                }
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Logs.warn("tmdb request failed", error)
            null
        }
    }

    companion object {
        fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }
        fun backdropUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    }
}
