package com.orgista.openstream.library

import com.orgista.openstream.Logs
import com.orgista.openstream.data.MediaKind
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
    val overview: String? = null,
    val releaseYear: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val trailerYoutubeKey: String? = null,
)

/**
 * TMDB metadata client. Accepts either TMDB credential the user can paste in
 * Settings: the v3 API key (a 32-char hex string, sent as an `api_key` query
 * param) or the v4 API Read Access Token (a long JWT starting with `eyJ`, sent
 * as an `Authorization: Bearer` header). Either unlocks posters, backdrops,
 * overviews, and certifications — the same source Infuse and Jellyfin scrape.
 * Without a key the library still organizes offline from parsed filenames.
 */
class Tmdb(private val apiKey: String) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    // A v4 Read Access Token is a JWT ("eyJ…"); anything else is treated as a
    // v3 key and appended to the query string.
    private val bearerToken: String? = apiKey.takeIf { it.startsWith("eyJ") }

    fun searchMovie(title: String, year: Int?): TmdbResult? {
        if (!enabled) return null
        val url = buildString {
            append("https://api.themoviedb.org/3/search/movie?query=")
            append(URLEncoder.encode(title, "UTF-8"))
            if (year != null) append("&year=$year")
        }
        val first = firstResult(url) ?: return null
        return toResult(first, isMovie = true)
    }

    fun searchShow(title: String): TmdbResult? {
        if (!enabled) return null
        val url = "https://api.themoviedb.org/3/search/tv?query=" +
            URLEncoder.encode(title, "UTF-8")
        val first = firstResult(url) ?: return null
        return toResult(first, isMovie = false)
    }

    /** Resolves the canonical TMDB record when an add-on already supplied an IMDb id. */
    fun findByImdbId(imdbId: String, kind: MediaKind): TmdbResult? {
        if (!enabled || !imdbId.matches(Regex("tt\\d{5,12}"))) return null
        val payload = getJson(
            "https://api.themoviedb.org/3/find/${URLEncoder.encode(imdbId, "UTF-8")}" +
                "?external_source=imdb_id",
        ) ?: return null
        return parseFindResult(payload.toString(), kind)
    }

    fun parseFindResult(rawJson: String, kind: MediaKind): TmdbResult? {
        val payload = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val key = if (kind == MediaKind.SERIES) "tv_results" else "movie_results"
        val first = payload.optJSONArray(key)?.optJSONObject(0) ?: return null
        return toResult(first, isMovie = kind != MediaKind.SERIES)
    }

    /** Movie detail with credits, IMDb id, and US certification in one call. */
    fun movieDetails(tmdbId: Int): TmdbDetails? {
        if (!enabled) return null
        val json = getJson(
            "https://api.themoviedb.org/3/movie/$tmdbId?append_to_response=credits,release_dates,videos",
        ) ?: return null
        val cert = usCertification(json.optJSONObject("release_dates"), "movie")
        return toDetails(json, json.optString("imdb_id").takeIf { it.isNotBlank() }, cert, isMovie = true)
    }

    fun showDetails(tmdbId: Int): TmdbDetails? {
        if (!enabled) return null
        val json = getJson(
            "https://api.themoviedb.org/3/tv/$tmdbId?append_to_response=credits,external_ids,content_ratings,videos",
        ) ?: return null
        val imdb = json.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() }
        val cert = usCertification(json.optJSONObject("content_ratings"), "tv")
        return toDetails(json, imdb, cert, isMovie = false)
    }

    private fun toDetails(json: JSONObject, imdbId: String?, cert: String?, isMovie: Boolean): TmdbDetails {
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
        val date = json.optString(if (isMovie) "release_date" else "first_air_date")
        return TmdbDetails(
            imdbId = imdbId,
            runtimeMinutes = runtime,
            genres = genres,
            cast = cast,
            director = director,
            tagline = json.optString("tagline").takeIf { it.isNotBlank() },
            certification = cert,
            overview = json.optString("overview").takeIf { it.isNotBlank() },
            releaseYear = date.takeIf { it.length >= 4 }?.take(4)?.toIntOrNull(),
            posterPath = json.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
            backdropPath = json.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
            trailerYoutubeKey = selectTrailerKey(json.optJSONObject("videos")),
        )
    }

    fun parseTrailerKey(rawJson: String): String? =
        runCatching { selectTrailerKey(JSONObject(rawJson)) }.getOrNull()

    private fun selectTrailerKey(videos: JSONObject?): String? {
        val results = videos?.optJSONArray("results") ?: return null
        return (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .mapNotNull { video ->
                val site = video.optString("site")
                val type = video.optString("type")
                val key = video.optString("key")
                if (!site.equals("YouTube", ignoreCase = true) ||
                    type !in setOf("Trailer", "Teaser") ||
                    !key.matches(Regex("^[A-Za-z0-9_-]{6,24}$"))
                ) {
                    return@mapNotNull null
                }
                val score = (if (type == "Trailer") 200 else 100) +
                    (if (video.optBoolean("official")) 40 else 0) +
                    (if (video.optString("iso_639_1").equals("en", ignoreCase = true)) 20 else 0) +
                    (if (video.optString("iso_3166_1").equals("US", ignoreCase = true)) 5 else 0)
                key to score
            }
            .maxByOrNull { it.second }
            ?.first
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
            // v3: append the key to the query. v4: leave the URL alone and
            // authenticate with the bearer header instead.
            val requestUrl = if (bearerToken == null) {
                url + (if ('?' in url) "&" else "?") + "api_key=" + apiKey
            } else {
                url
            }
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
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
            // URL/cause text can contain a v3 key; log only the failure type.
            Logs.warn("tmdb request failed (${error::class.simpleName})")
            null
        }
    }

    companion object {
        fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w500$it" }
        fun backdropUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    }
}
