package com.orgista.openstream.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackProgressPolicyTest {
    @Test
    fun `resume returns a meaningful saved position`() {
        assertEquals(90_000L, PlaybackProgressPolicy.resumePosition(90_000L, 600_000L))
    }

    @Test
    fun `resume ignores opening seconds and completed titles`() {
        assertEquals(0L, PlaybackProgressPolicy.resumePosition(8_000L, 600_000L))
        assertEquals(0L, PlaybackProgressPolicy.resumePosition(580_000L, 600_000L))
    }

    @Test
    fun `catalog progress only exposes resumable playback`() {
        assertEquals(25, PlaybackProgressPolicy.progressPercent(150_000L, 600_000L))
        assertNull(PlaybackProgressPolicy.progressPercent(580_000L, 600_000L))
    }
}
