package com.orgista.openstream.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveVideoFreezeRecoveryPolicyTest {
    @Test
    fun `restarts a live video route when playback advances without frames`() {
        assertEquals(
            LiveVideoFreezeRecoveryAction.RESTART_ROUTE,
            LiveVideoFreezeRecoveryPolicy.action(
                isLive = true,
                isPlaying = true,
                hasSelectedVideo = true,
                playbackPositionAdvanced = true,
                millisSinceLastVideoFrame = LiveVideoFreezeRecoveryPolicy.STALL_TIMEOUT_MS,
                recoveryAttempts = 0,
            ),
        )
    }

    @Test
    fun `advances to another route when one restart did not restore frames`() {
        assertEquals(
            LiveVideoFreezeRecoveryAction.ADVANCE_ROUTE,
            LiveVideoFreezeRecoveryPolicy.action(
                isLive = true,
                isPlaying = true,
                hasSelectedVideo = true,
                playbackPositionAdvanced = true,
                millisSinceLastVideoFrame = LiveVideoFreezeRecoveryPolicy.STALL_TIMEOUT_MS + 1,
                recoveryAttempts = 1,
            ),
        )
    }

    @Test
    fun `does not recover normal buffering audio-only or non-live playback`() {
        val common = LiveVideoFreezeRecoveryPolicy.STALL_TIMEOUT_MS * 2
        assertEquals(
            LiveVideoFreezeRecoveryAction.NONE,
            LiveVideoFreezeRecoveryPolicy.action(true, false, true, true, common, 0),
        )
        assertEquals(
            LiveVideoFreezeRecoveryAction.NONE,
            LiveVideoFreezeRecoveryPolicy.action(true, true, false, true, common, 0),
        )
        assertEquals(
            LiveVideoFreezeRecoveryAction.NONE,
            LiveVideoFreezeRecoveryPolicy.action(true, true, true, false, common, 0),
        )
        assertEquals(
            LiveVideoFreezeRecoveryAction.NONE,
            LiveVideoFreezeRecoveryPolicy.action(false, true, true, true, common, 0),
        )
        assertEquals(
            LiveVideoFreezeRecoveryAction.NONE,
            LiveVideoFreezeRecoveryPolicy.action(
                true,
                true,
                true,
                true,
                LiveVideoFreezeRecoveryPolicy.STALL_TIMEOUT_MS - 1,
                0,
            ),
        )
    }
}
