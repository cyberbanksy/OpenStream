package com.orgista.openstream.offline

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.StremioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStreamIdentityPolicyTest {
    private val spiderVerse = MediaItem(
        id = "tt9362722",
        kind = MediaKind.MOVIE,
        title = "Spider-Man: Across the Spider-Verse",
        synopsis = "",
        year = 2023,
        rating = "PG",
        runtimeMinutes = 140,
        imdbId = "tt9362722",
    )

    @Test
    fun `different movie filename is rejected`() {
        val stream = StremioStream(
            title = "1080p HEVC",
            url = "https://stream.example/movie.mkv",
            filename = "Back.to.the.Future.1985.1080p.BluRay.mkv",
        )

        assertEquals(OfflineIdentityResult.MISMATCH, OfflineStreamIdentityPolicy.classify(spiderVerse, stream))
        assertFalse(OfflineStreamIdentityPolicy.isVerifiedMatch(spiderVerse, stream))
    }

    @Test
    fun `matching movie filename is verified`() {
        val stream = StremioStream(
            title = "2160p Dolby Vision",
            url = "https://stream.example/movie.mkv",
            filename = "Spider-Man.Across.the.Spider-Verse.2023.2160p.DV.mkv",
        )

        assertEquals(OfflineIdentityResult.MATCH, OfflineStreamIdentityPolicy.classify(spiderVerse, stream))
        assertTrue(OfflineStreamIdentityPolicy.isVerifiedMatch(spiderVerse, stream))
    }

    @Test
    fun `quality-only stream label remains unverified`() {
        val stream = StremioStream("1080p HEVC 5.1", "https://stream.example/movie.mkv")

        assertEquals(OfflineIdentityResult.UNKNOWN, OfflineStreamIdentityPolicy.classify(spiderVerse, stream))
        assertFalse(OfflineStreamIdentityPolicy.isVerifiedMatch(spiderVerse, stream))
    }
}
