package com.orgista.openstream.streaming

import com.orgista.openstream.data.StremioStream
import com.orgista.openstream.offline.OfflineDeviceProfile
import com.orgista.openstream.offline.OfflineDynamicRange
import com.orgista.openstream.offline.OfflineVideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingQualityPolicyTest {
    private val profile = OfflineDeviceProfile(
        maxWidth = 3840,
        maxHeight = 2160,
        codecs = setOf(OfflineVideoCodec.AVC, OfflineVideoCodec.HEVC, OfflineVideoCodec.AV1),
        dynamicRanges = setOf(OfflineDynamicRange.SDR, OfflineDynamicRange.HDR10, OfflineDynamicRange.DOLBY_VISION),
        dolbyAtmos = true,
        dolbyVisionProfiles = setOf(4, 5, 8, 9),
    )

    private val streams = listOf(
        StremioStream("2160p DV HEVC TrueHD REMUX 55 GB", "https://stream.test/remux"),
        StremioStream("1080p HEVC 9 GB", "https://stream.test/9gb"),
        StremioStream("480p H.264 1.5 GB", "https://stream.test/small"),
    )

    @Test
    fun `slow connection selects the smallest sustainable stream`() {
        val selection = StreamingQualityPolicy.select(streams, profile, runtimeMinutes = 120, bandwidthMbps = 3.0)

        assertEquals("https://stream.test/small", selection?.selected?.stream?.url)
        assertTrue(selection!!.plan.sustainable)
        assertTrue(selection.plan.downsizedForBandwidth)
    }

    @Test
    fun `faster connection selects nine gigabyte 1080p instead of oversized remux`() {
        val selection = StreamingQualityPolicy.select(streams, profile, runtimeMinutes = 120, bandwidthMbps = 20.0)

        assertEquals("https://stream.test/9gb", selection?.selected?.stream?.url)
        assertTrue(selection!!.plan.statusText.contains("9.0 GB"))
    }

    @Test
    fun `insufficient connection reports cache and wait needed to avoid buffering`() {
        val selection = StreamingQualityPolicy.select(
            listOf(StremioStream("1080p HEVC 9 GB", "https://stream.test/only")),
            profile,
            runtimeMinutes = 120,
            bandwidthMbps = 4.0,
        )

        assertFalse(selection!!.plan.sustainable)
        assertTrue(selection.plan.requiredCacheMinutes > 0)
        assertTrue(selection.plan.readyWaitMinutes > selection.plan.requiredCacheMinutes)
        assertTrue(selection.plan.statusText.contains("cache"))
    }

    @Test
    fun `unprofiled dolby vision is fallback when a reliable route exists`() {
        val selection = StreamingQualityPolicy.select(
            listOf(
                StremioStream("2160p BluRay REMUX DV HEVC 8 GB", "https://stream.test/dv"),
                StremioStream("2160p HDR10 HEVC 8 GB", "https://stream.test/hdr10"),
            ),
            profile,
            runtimeMinutes = 120,
            bandwidthMbps = 30.0,
        )

        assertEquals("https://stream.test/hdr10", selection?.selected?.stream?.url)
        assertEquals("https://stream.test/dv", selection?.fallbacks?.lastOrNull()?.stream?.url)
    }

    @Test
    fun `sustainable profile eight dolby vision outranks hdr10`() {
        val selection = StreamingQualityPolicy.select(
            listOf(
                StremioStream("2160p DV P8 HEVC 4.86 GB", "https://stream.test/p8"),
                StremioStream("2160p HDR10 HEVC 6.9 GB", "https://stream.test/hdr10"),
            ),
            profile,
            runtimeMinutes = 148,
            bandwidthMbps = 20.5,
        )

        assertEquals("https://stream.test/p8", selection?.selected?.stream?.url)
        assertTrue(selection!!.plan.qualityLabel.contains("Dolby Vision"))
    }

    @Test
    fun `unprofiled dolby vision x265 encode remains eligible for runtime profile probe`() {
        val selection = StreamingQualityPolicy.select(
            listOf(
                StremioStream("2160p 10bit HDR DV BluRay 8CH x265 HEVC 4.86 GB", "https://stream.test/dv-web"),
                StremioStream("2160p HDR10 HEVC 6 GB", "https://stream.test/hdr10"),
            ),
            profile,
            runtimeMinutes = 148,
            bandwidthMbps = 20.5,
        )

        assertEquals("https://stream.test/dv-web", selection?.selected?.stream?.url)
    }

    @Test
    fun `cache estimate follows bitrate deficit`() {
        val readiness = StreamingReadinessPolicy.assess(
            estimatedBitrateMbps = 8.0,
            bandwidthMbps = 4.0,
            runtimeMinutes = 120,
        )

        assertEquals(60.0, readiness.requiredCacheMinutes, 0.01)
        assertEquals(120.0, readiness.readyWaitMinutes, 0.01)
    }
}
