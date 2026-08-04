package com.orgista.openstream.library

import com.orgista.openstream.Logs
import com.orgista.openstream.data.TitleRatings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OmdbResult(
    val ratings: TitleRatings,
    val rated: String?,
    val director: String?,
    val actors: List<String>,
    val genres: List<String>,
    val runtimeMinutes: Int,
)

/**
 * OMDb is the source that carries Rotten Tomatoes and Metacritic scores
 * (alongside IMDb) for a title, keyed by IMDb id — the same aggregation
 * Infuse shows. A free key is required; enrichment is skipped without one.
 */
class Omdb(private val apiKey: String) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    fun byImdbId(imdbId: String): OmdbResult? {
        if (!enabled || imdbId.isBlank()) return null
        val json = getJson("https://www.omdbapi.com/?apikey=$apiKey&i=$imdbId&tomatoes=true") ?: return null
        if (json.optString("Response") == "False") return null

        var imdb: String? = json.optString("imdbRating").takeIf { it.isNotBlank() && it != "N/A" }?.let { "$it/10" }
        var rt: String? = null
        var meta: String? = null
        json.optJSONArray("Ratings")?.let { array ->
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                when (entry.optString("Source")) {
                    "Rotten Tomatoes" -> rt = entry.optString("Value").takeIf { it.isNotBlank() }
                    "Metacritic" -> meta = entry.optString("Value").takeIf { it.isNotBlank() }
                    "Internet Movie Database" -> if (imdb == null) imdb = entry.optString("Value")
                }
            }
        }

        fun list(key: String) = json.optString(key)
            .takeIf { it.isNotBlank() && it != "N/A" }
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val runtime = json.optString("Runtime")
            .removeSuffix(" min").trim().toIntOrNull() ?: 0

        return OmdbResult(
            ratings = TitleRatings(imdb = imdb, rottenTomatoes = rt, metacritic = meta),
            rated = json.optString("Rated").takeIf { it.isNotBlank() && it != "N/A" },
            director = list("Director").firstOrNull(),
            actors = list("Actors"),
            genres = list("Genre"),
            runtimeMinutes = runtime,
        )
    }

    private fun getJson(url: String): JSONObject? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) {
                Logs.warn("omdb HTTP ${connection.responseCode}")
                null
            } else {
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    } catch (error: Exception) {
        Logs.warn("omdb request failed", error)
        null
    }
}
