package com.orgista.openstream.player

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.domain.ViewingSignal
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesPlaybackPolicyTest {
    private fun episode(number: Int) = MediaItem(
        id = "tt1234567:1:$number",
        kind = MediaKind.SERIES,
        title = "Series",
        subtitle = "S1 E$number · Episode $number",
        synopsis = "",
        year = 2025,
        rating = "TV-14",
        runtimeMinutes = 24,
        imdbId = "tt1234567",
    )

    private val episodes = (1..4).map(::episode)

    @Test
    fun `unwatched series begins with first episode`() {
        val decision = SeriesPlaybackPolicy.select(episodes, emptyMap(), emptySet())

        assertEquals(episodes[0], decision?.episode)
        assertEquals(SeriesPlaybackAction.FIRST, decision?.action)
    }

    @Test
    fun `latest unfinished episode resumes`() {
        val history = mapOf(
            episodes[0].id to ViewingSignal(100L, 1, completed = true),
            episodes[1].id to ViewingSignal(200L, 1, completed = false),
        )
        val decision = SeriesPlaybackPolicy.select(episodes, history, setOf(episodes[1].id))

        assertEquals(episodes[1], decision?.episode)
        assertEquals(SeriesPlaybackAction.RESUME, decision?.action)
    }

    @Test
    fun `completed latest episode advances to next episode`() {
        val history = mapOf(episodes[1].id to ViewingSignal(300L, 1, completed = true))
        val decision = SeriesPlaybackPolicy.select(episodes, history, emptySet())

        assertEquals(episodes[2], decision?.episode)
        assertEquals(SeriesPlaybackAction.NEXT, decision?.action)
    }

    @Test
    fun `unfinished episode without meaningful timestamp restarts same episode`() {
        val history = mapOf(episodes[2].id to ViewingSignal(400L, 1, completed = false))
        val decision = SeriesPlaybackPolicy.select(episodes, history, emptySet())

        assertEquals(episodes[2], decision?.episode)
        assertEquals(SeriesPlaybackAction.CONTINUE, decision?.action)
    }

    @Test
    fun `completed finale stays available for replay`() {
        val history = mapOf(episodes.last().id to ViewingSignal(500L, 1, completed = true))
        val decision = SeriesPlaybackPolicy.select(episodes, history, emptySet())

        assertEquals(episodes.last(), decision?.episode)
        assertEquals(SeriesPlaybackAction.REPLAY, decision?.action)
    }
}
