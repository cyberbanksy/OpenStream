package com.orgista.openstream.ui

import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRoutingTest {
    private fun item(candidates: List<MediaCandidate>) = MediaItem(
        id = "northstar",
        kind = MediaKind.MOVIE,
        title = "Northstar",
        synopsis = "",
        year = 2026,
        rating = "PG-13",
        runtimeMinutes = 127,
        candidates = candidates,
    )

    @Test
    fun `preferred request uses the first playable route`() {
        val request = preferredPlaybackRequest(
            item(
                listOf(
                    MediaCandidate(
                        id = "nas-1",
                        sourceName = "ServerPlus",
                        sourceKind = SourceKind.NAS,
                        uri = "smb://server/media/Northstar.mkv",
                        formatLabel = "MKV",
                    ),
                ),
            ),
        )

        assertEquals("Northstar", request?.title)
        assertEquals("ServerPlus", request?.subtitle)
        assertEquals("smb://server/media/Northstar.mkv", request?.uri)
    }

    @Test
    fun `item without a route cannot create a direct request`() {
        assertNull(preferredPlaybackRequest(item(emptyList())))
    }

    @Test
    fun `configured stream routes are deduplicated and bounded for failover`() {
        val request = PlaybackRequest(
            title = "Film",
            subtitle = "Primary",
            uri = "https://stream.test/one",
            fallbackUris = listOf(
                "https://stream.test/one",
                "https://stream.test/two",
                "javascript:bad",
            ) + (3..30).map { "https://stream.test/$it" },
        )

        val routes = playableUris(request)

        assertEquals("https://stream.test/one", routes.first())
        assertEquals("https://stream.test/two", routes[1])
        assertEquals(12, routes.size)
    }
}
