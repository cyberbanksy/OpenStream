package com.orgista.openstream.domain

import com.orgista.openstream.data.AddonCatalogSection
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class UpNextPolicyTest {
    private fun item(id: String, kind: MediaKind = MediaKind.MOVIE, progress: Int? = null) = MediaItem(
        id = id,
        kind = kind,
        title = id,
        synopsis = "",
        year = 2026,
        rating = "PG",
        runtimeMinutes = 90,
        progressPercent = progress,
    )

    @Test
    fun `up next starts with resume then one latest upload per channel and likely titles`() {
        val resume = item("resume", progress = 48)
        val completed = item("completed")
        val likely = item("likely")
        val videoOne = item("youtube:aaaaaaaaaaa", MediaKind.VIDEO)
        val videoTwo = item("youtube:bbbbbbbbbbb", MediaKind.VIDEO)
        val youtube = listOf(
            AddonCatalogSection("youtube:one", "YouTube", "one", "Channel One", listOf(videoOne)),
            AddonCatalogSection("youtube:two", "YouTube", "two", "Channel Two", listOf(videoTwo)),
        )

        val selected = UpNextPolicy.select(
            rankedCatalog = listOf(resume, completed, likely, videoOne, videoTwo),
            youtubeSections = youtube,
            history = mapOf("completed" to ViewingSignal(100L, 1, completed = true)),
        )

        assertEquals(listOf("resume", "youtube:aaaaaaaaaaa", "youtube:bbbbbbbbbbb", "likely"), selected.map { it.id })
    }

    @Test
    fun `up next is bounded and never duplicates a title`() {
        val repeated = item("same", progress = 20)
        val selected = UpNextPolicy.select(
            rankedCatalog = listOf(repeated, repeated) + (1..20).map { item("movie-$it") },
            youtubeSections = emptyList(),
            history = emptyMap(),
            limit = 8,
        )

        assertEquals(8, selected.size)
        assertEquals(selected.map { it.id }.distinct(), selected.map { it.id })
    }
}
