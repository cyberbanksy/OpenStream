package com.orgista.openstream.player

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.domain.ViewingSignal

enum class SeriesPlaybackAction { FIRST, RESUME, CONTINUE, NEXT, REPLAY }

data class SeriesPlaybackDecision(
    val episode: MediaItem,
    val action: SeriesPlaybackAction,
)

/** Selects one deterministic episode from local, privacy-preserving playback history. */
object SeriesPlaybackPolicy {
    private val seasonPattern = Regex("(?i)^S(\\d{1,3})\\s*E\\d{1,4}")

    fun select(
        episodes: List<MediaItem>,
        history: Map<String, ViewingSignal>,
        resumableIds: Set<String>,
    ): SeriesPlaybackDecision? {
        if (episodes.isEmpty()) return null
        val mainSequence = episodes.filter { (season(it) ?: 1) > 0 }.ifEmpty { episodes }
        val latest = episodes
            .mapNotNull { episode -> history[episode.id]?.let { episode to it } }
            .maxByOrNull { it.second.lastPlayedAtMs }
            ?: return SeriesPlaybackDecision(mainSequence.first(), SeriesPlaybackAction.FIRST)

        val (latestEpisode, signal) = latest
        if (!signal.completed) {
            val action = if (latestEpisode.id in resumableIds) {
                SeriesPlaybackAction.RESUME
            } else {
                SeriesPlaybackAction.CONTINUE
            }
            return SeriesPlaybackDecision(latestEpisode, action)
        }

        val sequence = if ((season(latestEpisode) ?: 1) > 0) mainSequence else episodes
        val next = sequence.getOrNull(sequence.indexOfFirst { it.id == latestEpisode.id } + 1)
        return if (next != null) {
            SeriesPlaybackDecision(next, SeriesPlaybackAction.NEXT)
        } else {
            SeriesPlaybackDecision(latestEpisode, SeriesPlaybackAction.REPLAY)
        }
    }

    private fun season(item: MediaItem): Int? = item.subtitle
        ?.let(seasonPattern::find)
        ?.groupValues?.get(1)?.toIntOrNull()
}
