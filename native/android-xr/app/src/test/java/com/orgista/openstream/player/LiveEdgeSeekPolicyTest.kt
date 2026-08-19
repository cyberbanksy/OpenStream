package com.orgista.openstream.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveEdgeSeekPolicyTest {
    @Test
    fun `already live resumes without seeking backward`() {
        assertEquals(
            LiveEdgeSeekDecision.Resume,
            LiveEdgeSeekPolicy.decide(
                timelineIsLive = true,
                currentPositionMs = 120_000L,
                durationMs = 180_000L,
                currentLiveOffsetMs = 1_500L,
            ),
        )
    }

    @Test
    fun `viewer behind live edge seeks forward with a playback cushion`() {
        assertEquals(
            LiveEdgeSeekDecision.SeekForward(133_000L),
            LiveEdgeSeekPolicy.decide(
                timelineIsLive = true,
                currentPositionMs = 120_000L,
                durationMs = 180_000L,
                currentLiveOffsetMs = 15_000L,
            ),
        )
    }

    @Test
    fun `seek never exceeds the current live window`() {
        assertEquals(
            LiveEdgeSeekDecision.SeekForward(179_500L),
            LiveEdgeSeekPolicy.decide(
                timelineIsLive = true,
                currentPositionMs = 175_000L,
                durationMs = 180_000L,
                currentLiveOffsetMs = 20_000L,
            ),
        )
    }

    @Test
    fun `window end is a forward fallback when live offset is unavailable`() {
        assertEquals(
            LiveEdgeSeekDecision.SeekForward(178_000L),
            LiveEdgeSeekPolicy.decide(
                timelineIsLive = true,
                currentPositionMs = 150_000L,
                durationMs = 180_000L,
                currentLiveOffsetMs = null,
            ),
        )
    }

    @Test
    fun `unrecognized live route reconnects rather than replaying its buffer`() {
        assertEquals(
            LiveEdgeSeekDecision.Reconnect,
            LiveEdgeSeekPolicy.decide(
                timelineIsLive = false,
                currentPositionMs = 30_000L,
                durationMs = null,
                currentLiveOffsetMs = null,
            ),
        )
    }
}
