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
