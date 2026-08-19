package com.orgista.openstream.arr

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ArrDownloadTarget(
    val title: String,
    val year: Int,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

data class ArrDownloadResult(val message: String)

object ArrDownloadClient {
    fun enqueue(connection: ArrConnection, target: ArrDownloadTarget): ArrDownloadResult = when (connection.kind) {
        ArrKind.RADARR -> enqueueMovie(connection, target)
        ArrKind.SONARR -> enqueueSeries(connection, target)
    }

    private fun enqueueMovie(connection: ArrConnection, target: ArrDownloadTarget): ArrDownloadResult {
        require(target.seasonNumber == null && target.episodeNumber == null) { "Radarr only accepts movies." }
        val lookup = lookup(
            connection,
            "/api/v3/movie/lookup",
            target.lookupTerm(),
            target,
        )
        val tmdbId = target.tmdbId?.takeIf { it > 0 } ?: lookup.optInt("tmdbId").takeIf { it > 0 }
            ?: throw IOException("Radarr could not identify ${target.title}.")
        val existing = jsonArray(
            ArrClient.request(connection, "GET", "/api/v3/movie?tmdbId=$tmdbId"),
        ).firstObjectOrNull()

        if (existing != null) {
            val movieId = existing.optInt("id").takeIf { it > 0 }
                ?: throw IOException("Radarr returned an invalid movie record.")
            ensureHighestProfile(connection, "/api/v3/movie/$movieId?moveFiles=false", existing)
            ArrClient.request(
                connection,
                "POST",
                "/api/v3/command",
                JSONObject().put("name", "MoviesSearch").put("movieIds", JSONArray().put(movieId)).toString(),
            )
        } else {
            val root = preferredRoot(connection)
            val profileId = preferredQualityProfileId(connection)
            val movie = JSONObject(lookup.toString())
                .removeServerFields()
                .put("tmdbId", tmdbId)
                .put("rootFolderPath", root)
                .put("qualityProfileId", profileId)
                .put("monitored", true)
                .put("minimumAvailability", "released")
                .put(
                    "addOptions",
                    JSONObject()
                        .put("monitor", "movieOnly")
                        .put("searchForMovie", true),
                )
            ArrClient.request(connection, "POST", "/api/v3/movie", movie.toString())
        }
        return ArrDownloadResult("Radarr is searching for ${target.title}")
    }

    private fun enqueueSeries(connection: ArrConnection, target: ArrDownloadTarget): ArrDownloadResult {
        val lookup = lookup(
            connection,
            "/api/v3/series/lookup",
            target.lookupTerm(preferImdb = true),
            target,
        )
        val tvdbId = lookup.optInt("tvdbId").takeIf { it > 0 }
            ?: throw IOException("Sonarr could not identify ${target.title}.")
        val existing = jsonArray(
            ArrClient.request(connection, "GET", "/api/v3/series?tvdbId=$tvdbId"),
        ).firstObjectOrNull()
        val seriesId = existing?.optInt("id")?.takeIf { it > 0 } ?: addSeries(connection, lookup, target)
        if (existing != null) {
            ensureHighestProfile(connection, "/api/v3/series/$seriesId?moveFiles=false", existing)
        }

        val command = when {
            target.episodeNumber != null -> {
                val season = target.seasonNumber
                    ?: throw IllegalArgumentException("An episode download requires a season number.")
                val episodes = jsonArray(
                    ArrClient.request(
                        connection,
                        "GET",
                        "/api/v3/episode?seriesId=$seriesId&seasonNumber=$season",
                    ),
                )
                val episodeId = episodes.objects()
                    .firstOrNull { it.optInt("episodeNumber") == target.episodeNumber }
                    ?.optInt("id")
                    ?.takeIf { it > 0 }
                    ?: throw IOException("Sonarr could not find Season $season Episode ${target.episodeNumber}.")
                JSONObject()
                    .put("name", "EpisodeSearch")
                    .put("episodeIds", JSONArray().put(episodeId))
            }
            target.seasonNumber != null -> JSONObject()
                .put("name", "SeasonSearch")
                .put("seriesId", seriesId)
                .put("seasonNumber", target.seasonNumber)
            else -> JSONObject()
                .put("name", "SeriesSearch")
                .put("seriesId", seriesId)
        }
        ArrClient.request(connection, "POST", "/api/v3/command", command.toString())

        val scope = when {
            target.episodeNumber != null -> " · Season ${target.seasonNumber} Episode ${target.episodeNumber}"
            target.seasonNumber != null -> " · Season ${target.seasonNumber}"
            else -> ""
        }
        return ArrDownloadResult("Sonarr is searching for ${target.title}$scope")
    }

    private fun addSeries(
        connection: ArrConnection,
        lookup: JSONObject,
        target: ArrDownloadTarget,
    ): Int {
        val monitor = if (target.seasonNumber == null) "all" else "none"
        val series = JSONObject(lookup.toString())
            .removeServerFields()
            .put("rootFolderPath", preferredRoot(connection))
            .put("qualityProfileId", preferredQualityProfileId(connection))
            .put("seasonFolder", true)
            .put("monitored", true)
            .put("monitorNewItems", "all")
            .put(
                "addOptions",
                JSONObject()
                    .put("monitor", monitor)
                    .put("searchForMissingEpisodes", false)
                    .put("searchForCutoffUnmetEpisodes", false),
            )
        return JSONObject(ArrClient.request(connection, "POST", "/api/v3/series", series.toString()))
            .optInt("id")
            .takeIf { it > 0 }
            ?: throw IOException("Sonarr did not return the added series.")
    }

    private fun lookup(
        connection: ArrConnection,
        endpoint: String,
        term: String,
        target: ArrDownloadTarget,
    ): JSONObject {
        val encoded = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val matches = jsonArray(ArrClient.request(connection, "GET", "$endpoint?term=$encoded")).objects()
        return matches.firstOrNull { candidate ->
            target.tmdbId?.takeIf { it > 0 }?.let { it == candidate.optInt("tmdbId") } == true ||
                target.imdbId?.equals(candidate.optString("imdbId"), ignoreCase = true) == true
        } ?: matches.firstOrNull { candidate ->
            candidate.optString("title").equals(target.title, ignoreCase = true) &&
                (target.year <= 0 || candidate.optInt("year") == target.year)
        } ?: matches.firstOrNull()
        ?: throw IOException("${connection.kind.displayName} could not find ${target.title}.")
    }

    private fun ArrDownloadTarget.lookupTerm(preferImdb: Boolean = false): String = when {
        preferImdb && !imdbId.isNullOrBlank() -> "imdb:$imdbId"
        tmdbId != null && tmdbId > 0 -> "tmdb:$tmdbId"
        !imdbId.isNullOrBlank() -> "imdb:$imdbId"
        title.isNotBlank() -> "$title ${year.takeIf { it > 0 } ?: ""}".trim()
        else -> throw IllegalArgumentException("A title or external ID is required.")
    }

    private fun preferredRoot(connection: ArrConnection): String {
        val roots = jsonArray(ArrClient.request(connection, "GET", "/api/v3/rootfolder")).objects()
        return (roots.firstOrNull { it.optBoolean("accessible", true) } ?: roots.firstOrNull())
            ?.optString("path")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("${connection.kind.displayName} has no usable root folder.")
    }

    private fun preferredQualityProfileId(connection: ArrConnection): Int {
        val profiles = jsonArray(ArrClient.request(connection, "GET", "/api/v3/qualityprofile"))
        return ArrQualityProfilePolicy.selectHighestProfileId(profiles)
            ?: throw IOException("${connection.kind.displayName} has no quality profile.")
    }

    private fun ensureHighestProfile(
        connection: ArrConnection,
        resourcePath: String,
        resource: JSONObject,
    ) {
        val currentProfileId = resource.optInt("qualityProfileId").takeIf { it > 0 } ?: return
        val highestProfileId = preferredQualityProfileId(connection)
        if (currentProfileId == highestProfileId) return
        val updated = JSONObject(resource.toString()).put("qualityProfileId", highestProfileId)
        ArrClient.request(connection, "PUT", resourcePath, updated.toString())
    }

    private fun jsonArray(raw: String): JSONArray = JSONArray(raw)

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull(::optJSONObject)

    private fun JSONArray.firstObjectOrNull(): JSONObject? = optJSONObject(0)

    private fun JSONObject.removeServerFields(): JSONObject = apply {
        remove("id")
        remove("path")
        remove("statistics")
    }
}
