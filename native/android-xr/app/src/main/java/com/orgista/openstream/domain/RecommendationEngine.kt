package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import java.util.Calendar
import kotlin.math.ln

data class ViewingSignal(
    val lastPlayedAtMs: Long,
    val playCount: Int,
    val completed: Boolean,
)

/** Deterministic, local recommendations based on resume intent and title metadata. */
object RecommendationEngine {
    fun rank(
        items: List<MediaItem>,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        history: Map<String, ViewingSignal> = emptyMap(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<MediaItem> {
        val inProgress = items.filter { it.progressPercent != null }
        val itemById = items.associateBy { it.id }
        val genreAffinity = HashMap<String, Double>()

        inProgress.flatMap { it.genres }.forEach { genre ->
            genreAffinity[genre.lowercase()] = (genreAffinity[genre.lowercase()] ?: 0.0) + 2.0
        }
        history.forEach { (id, signal) ->
            val watched = itemById[id] ?: return@forEach
            val ageDays = ((nowMs - signal.lastPlayedAtMs).coerceAtLeast(0L) / 86_400_000.0)
            val recency = 1.0 / (1.0 + ageDays / 30.0)
            val strength = (1.0 + ln(signal.playCount.coerceAtLeast(1).toDouble() + 1.0)) * recency
            watched.genres.forEach { genre ->
                genreAffinity[genre.lowercase()] = (genreAffinity[genre.lowercase()] ?: 0.0) + strength
            }
        }

        return items.sortedWith(
            compareByDescending<MediaItem> { item ->
                val progressScore = item.progressPercent?.let { 1_000 + it } ?: 0
                val genreScore = (item.genres.sumOf { genreAffinity[it.lowercase()] ?: 0.0 } * 80.0).toInt()
                val recencyScore = (40 - (currentYear - item.year).coerceAtLeast(0) * 4).coerceAtLeast(0)
                val metadataScore = item.genres.size.coerceAtMost(4) * 3 +
                    if (item.synopsis.isNotBlank()) 4 else 0
                // Completed titles teach affinity but move behind unwatched
                // choices, giving the ranking useful novelty and diversity.
                val watchedPenalty = history[item.id]?.let { if (it.completed) 700 else 80 } ?: 0
                progressScore + genreScore + recencyScore + metadataScore - watchedPenalty
            }.thenByDescending { it.year }.thenBy { it.title.lowercase() },
        )
    }
}
