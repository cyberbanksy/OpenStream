package com.orgista.openstream.media

import org.junit.Assert.assertEquals
import org.junit.Test

class DolbyVisionPlaybackPolicyTest {
    private val tabletProfiles = setOf(4, 5, 8, 9)

    @Test
    fun `profile eight is allowed and profile seven falls back`() {
        assertEquals(
            DolbyVisionCompatibility.SUPPORTED,
            DolbyVisionPlaybackPolicy.compatibility("video/dolby-vision", "dvhe.08.06", tabletProfiles),
        )
        assertEquals(
            DolbyVisionCompatibility.UNSUPPORTED,
            DolbyVisionPlaybackPolicy.compatibility("video/dolby-vision", "dvhe.07.06", tabletProfiles),
        )
    }

    @Test
    fun `unprofiled dolby vision is probed while ordinary hevc is ignored`() {
        assertEquals(
            DolbyVisionCompatibility.UNPROFILED,
            DolbyVisionPlaybackPolicy.compatibility("video/dolby-vision", null, tabletProfiles),
        )
        assertEquals(
            DolbyVisionCompatibility.NOT_DOLBY_VISION,
            DolbyVisionPlaybackPolicy.compatibility("video/hevc", "hvc1.2.4.L153", tabletProfiles),
        )
    }
}
