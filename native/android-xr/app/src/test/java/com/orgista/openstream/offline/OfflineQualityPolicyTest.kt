package com.orgista.openstream.offline

import com.orgista.openstream.data.StremioStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQualityPolicyTest {
    @Test
    fun parsesResolutionCodecDynamicRangeAudioAndSize() {
        val descriptor = OfflineStreamDescriptor.parse(
            StremioStream("4K Dolby Vision · HEVC · Dolby Atmos · 12.5 GB", "https://media.example/movie.mkv"),
        )

        assertEquals(2160, descriptor.height)
        assertEquals(OfflineVideoCodec.HEVC, descriptor.codec)
        assertEquals(OfflineDynamicRange.DOLBY_VISION, descriptor.dynamicRange)
        assertTrue(descriptor.dolbyAtmos)
        assertEquals(13_421_772_800L, descriptor.sizeBytes)
    }

    @Test
    fun selectsHighestQualityThatFitsDisplayAndHardware() {
        val streams = listOf(
            StremioStream("4K Dolby Vision HEVC 8 GB", "https://media.example/4k.mkv"),
            StremioStream("1080p HDR10 HEVC 3 GB", "https://media.example/1080.mkv"),
            StremioStream("720p H.264 1 GB", "https://media.example/720.mp4"),
        )
        val profile = OfflineDeviceProfile(
            maxWidth = 1920,
            maxHeight = 1080,
            codecs = setOf(OfflineVideoCodec.AVC, OfflineVideoCodec.HEVC),
            dynamicRanges = setOf(OfflineDynamicRange.SDR, OfflineDynamicRange.HDR10),
            dolbyAtmos = false,
        )

        val selected = OfflineQualityPolicy.select(streams, profile, availableBytes = 10_000_000_000L)

        assertEquals("https://media.example/1080.mkv", selected?.stream?.url)
    }

    @Test
    fun prefersDolbyVisionAndAtmosWhenTheDeviceSupportsThem() {
        val streams = listOf(
            StremioStream("2160p HDR10 HEVC 7 GB", "https://media.example/hdr10.mkv"),
            StremioStream("2160p DV HEVC Atmos 7.5 GB", "https://media.example/dv.mkv"),
        )
        val profile = OfflineDeviceProfile(
            maxWidth = 3840,
            maxHeight = 2160,
            codecs = setOf(OfflineVideoCodec.HEVC),
            dynamicRanges = setOf(
                OfflineDynamicRange.SDR,
                OfflineDynamicRange.HDR10,
                OfflineDynamicRange.DOLBY_VISION,
            ),
            dolbyAtmos = true,
        )

        val selected = OfflineQualityPolicy.select(streams, profile, availableBytes = 20_000_000_000L)

        assertEquals("https://media.example/dv.mkv", selected?.stream?.url)
    }

    @Test
    fun rejectsKnownFilesThatDoNotFitAvailableStorage() {
        val streams = listOf(
            StremioStream("2160p HEVC 20 GB", "https://media.example/large.mkv"),
            StremioStream("1080p HEVC 3 GB", "https://media.example/fits.mkv"),
        )
        val profile = OfflineDeviceProfile(
            maxWidth = 3840,
            maxHeight = 2160,
            codecs = setOf(OfflineVideoCodec.HEVC),
            dynamicRanges = setOf(OfflineDynamicRange.SDR),
            dolbyAtmos = false,
        )

        val selected = OfflineQualityPolicy.select(streams, profile, availableBytes = 5_000_000_000L)

        assertEquals("https://media.example/fits.mkv", selected?.stream?.url)
        assertFalse(selected!!.sizeBytes > 5_000_000_000L)
    }
}
