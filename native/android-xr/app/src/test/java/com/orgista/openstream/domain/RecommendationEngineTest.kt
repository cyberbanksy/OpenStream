package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationEngineTest {
    private fun movie(
        id: String,
        year: Int,
        progress: Int? = null,
        genres: List<String> = emptyList(),
    ) = MediaItem(
        id = id,
        kind = MediaKind.MOVIE,
        title = id,
        synopsis = "Synopsis",
        year = year,
        rating = "PG",
        runtimeMinutes = 100,
        progressPercent = progress,
        genres = genres,
    )

    @Test
    fun `continue watching is ranked first`() {
        val ranked = RecommendationEngine.rank(
            listOf(movie("new", 2026), movie("resume", 2020, progress = 45)),
            currentYear = 2026,
        )

        assertEquals("resume", ranked.first().id)
    }

    @Test
    fun `genres from continue watching influence the next recommendations`() {
        val ranked = RecommendationEngine.rank(
            listOf(
                movie("resume", 2020, progress = 45, genres = listOf("Science Fiction")),
                movie("matching", 2021, genres = listOf("Science Fiction")),
                movie("newer", 2026, genres = listOf("Comedy")),
            ),
            currentYear = 2026,
        )

        assertEquals(listOf("resume", "matching", "newer"), ranked.map { it.id })
    }

    @Test
    fun `recent repeated plays strengthen similar recommendations without uploading history`() {
        val now = 2_000_000L
        val ranked = RecommendationEngine.rank(
            items = listOf(
                movie("watched", 2020, genres = listOf("Thriller")),
                movie("similar", 2019, genres = listOf("Thriller")),
                movie("unrelated", 2026, genres = listOf("Comedy")),
            ),
            currentYear = 2026,
            history = mapOf(
                "watched" to ViewingSignal(lastPlayedAtMs = now - 500L, playCount = 3, completed = true),
            ),
            nowMs = now,
        )

        assertEquals("similar", ranked.first().id)
        assertEquals("watched", ranked.last().id)
    }
}
