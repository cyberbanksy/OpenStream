package com.orgista.openstream.trakt

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import org.json.JSONObject

enum class TraktMediaType { MOVIE, EPISODE }

data class TraktMediaIdentity(
    val type: TraktMediaType,
    val imdbId: String,
    val title: String,
    val year: Int?,
    val season: Int? = null,
    val episode: Int? = null,
) {
    companion object {
        private val episodePattern = Regex("(?i)S(\\d{1,2})\\s*E(\\d{1,3})")

        fun from(item: MediaItem): TraktMediaIdentity? {
            val imdb = item.imdbId?.takeIf { it.matches(Regex("tt\\d{5,12}")) } ?: return null
            val episode = item.subtitle?.let(episodePattern::find)
            return if (item.kind == MediaKind.SERIES) {
                if (episode == null) return null
                TraktMediaIdentity(
                    type = TraktMediaType.EPISODE,
                    imdbId = imdb,
                    title = item.title,
                    year = item.year.takeIf { it > 0 },
                    season = episode.groupValues[1].toInt(),
                    episode = episode.groupValues[2].toInt(),
                )
            } else {
                TraktMediaIdentity(TraktMediaType.MOVIE, imdb, item.title, item.year.takeIf { it > 0 })
            }
        }
    }
}

object TraktPayloads {
    fun scrobble(identity: TraktMediaIdentity, progress: Double): JSONObject = JSONObject()
        .put("progress", progress.coerceIn(0.0, 100.0))
        .apply {
            when (identity.type) {
                TraktMediaType.MOVIE -> put(
                    "movie",
                    JSONObject()
                        .put("title", identity.title)
                        .put("year", identity.year)
                        .put("ids", JSONObject().put("imdb", identity.imdbId)),
                )
                TraktMediaType.EPISODE -> {
                    put(
                        "show",
                        JSONObject()
                            .put("title", identity.title)
                            .put("year", identity.year)
                            .put("ids", JSONObject().put("imdb", identity.imdbId)),
                    )
                    put("episode", JSONObject().put("season", identity.season).put("number", identity.episode))
                }
            }
        }
}
