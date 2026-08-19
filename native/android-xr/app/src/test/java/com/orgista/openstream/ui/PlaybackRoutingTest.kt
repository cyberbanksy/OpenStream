package com.orgista.openstream.ui

import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.SourceKind
import com.orgista.openstream.data.StremioStream
import com.orgista.openstream.offline.OfflineDeviceProfile
import com.orgista.openstream.offline.OfflineDynamicRange
import com.orgista.openstream.offline.OfflineVideoCodec
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
    fun `single play action prefers a completed device download over network routes`() {
        val request = bestPlaybackRequest(
            item(
                listOf(
                    MediaCandidate("nas", "Home server", SourceKind.NAS, "smb://server/movie.mkv", "4K"),
                    MediaCandidate(
                        "offline",
                        "On this device",
                        SourceKind.OFFLINE,
                        "https://stream.test/movie.m3u8",
                        "1080p",
                        offlineDownloadId = "download-1",
                    ),
                ),
            ),
        )

        assertEquals("On this device", request?.subtitle)
        assertEquals("download-1", request?.offlineDownloadId)
    }

    @Test
    fun `legacy unverified offline route is quarantined`() {
        val request = bestPlaybackRequest(
            item(
                listOf(
                    MediaCandidate(
                        "offline",
                        "On this device",
                        SourceKind.OFFLINE,
                        "https://stream.test/wrong.m3u8",
                        "1080p",
                        offlineDownloadId = "legacy-download",
                        offlineIdentityVerified = false,
                    ),
                    MediaCandidate("nas", "Home server", SourceKind.NAS, "smb://server/correct.mkv", "4K"),
                ),
            ),
        )

        assertEquals("Home server", request?.subtitle)
    }

    @Test
    fun `single play action chooses the best compatible addon stream for the device`() {
        val profile = OfflineDeviceProfile(
            maxWidth = 1920,
            maxHeight = 1080,
            codecs = setOf(OfflineVideoCodec.AVC),
            dynamicRanges = setOf(OfflineDynamicRange.SDR),
            dolbyAtmos = false,
        )
        val request = bestPlaybackRequest(
            item(emptyList()),
            streams = listOf(
                StremioStream("4K HEVC Dolby Vision", "https://stream.test/4k"),
                StremioStream("1080p H.264", "https://stream.test/1080"),
                StremioStream("720p H.264", "https://stream.test/720"),
            ),
            deviceProfile = profile,
        )

        assertEquals("https://stream.test/1080", request?.uri)
        assertEquals("1080p H.264", request?.subtitle)
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

    @Test
    fun `playback request retains episode identity for subtitle addons`() {
        val episode = MediaItem(
            id = "tt7654321:2:4",
            kind = MediaKind.SERIES,
            title = "Episode 4",
            synopsis = "",
            year = 2026,
            rating = "TV-14",
            runtimeMinutes = 52,
            imdbId = "tt7654321",
            candidates = listOf(
                MediaCandidate(
                    id = "episode-route",
                    sourceName = "Home server",
                    sourceKind = SourceKind.NAS,
                    uri = "smb://server/show/S02E04.mkv",
                    formatLabel = "MKV",
                ),
            ),
        )

        val request = bestPlaybackRequest(episode)

        assertEquals("series", request?.stremioType)
        assertEquals("tt7654321:2:4", request?.stremioId)
    }
}
