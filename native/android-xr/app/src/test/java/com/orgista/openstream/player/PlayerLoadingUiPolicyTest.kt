package com.orgista.openstream.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLoadingUiPolicyTest {
    @Test
    fun `only the branded preparation indicator is rendered`() {
        assertFalse(PlayerLoadingUiPolicy.showNativePlayerSpinner)
        assertTrue(PlayerLoadingUiPolicy.overlayWidthFraction <= 0.90f)
        assertTrue(PlayerLoadingUiPolicy.overlayMaxWidthDp <= 360)
        assertTrue(PlayerLoadingUiPolicy.spinnerSizeDp <= 30)
        assertTrue(PlayerLoadingUiPolicy.verticalPaddingDp <= 16)
        assertTrue(
            PlayerLoadingUiPolicy.showPreparationOverlay(
                statusVisible = true,
                statusText = "Preparing playback",
                hasTerminalError = false,
            ),
        )
    }

    @Test
    fun `terminal playback failure replaces rather than overlaps preparation`() {
        assertFalse(
            PlayerLoadingUiPolicy.showPreparationOverlay(
                statusVisible = true,
                statusText = "Preparing playback",
                hasTerminalError = true,
            ),
        )
    }

    @Test
    fun `a user paused player never shows a preparation overlay`() {
        assertFalse(
            PlayerLoadingUiPolicy.showPreparationOverlay(
                statusVisible = true,
                statusText = "Starting channel",
                hasTerminalError = false,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun `live playback starts a channel without claiming to optimize it`() {
        assertEquals(
            "Loading",
            PlayerLoadingUiPolicy.heading(
                isLive = true,
                isRemote = true,
                isBuffering = true,
                livePlaybackStarted = false,
            ),
        )
        assertEquals(
            "Loading",
            PlayerLoadingUiPolicy.heading(
                isLive = true,
                isRemote = true,
                isBuffering = true,
                livePlaybackStarted = true,
            ),
        )
        assertEquals(
            "Optimizing for your network",
            PlayerLoadingUiPolicy.heading(isLive = false, isRemote = true, isBuffering = true),
        )
        assertEquals(
            "Preparing playback",
            PlayerLoadingUiPolicy.heading(isLive = false, isRemote = false, isBuffering = true),
        )
    }

    @Test
    fun `live http client identifies itself and follows media redirects`() {
        assertTrue(PlaybackHttpPolicy.followCrossProtocolRedirects)
        assertTrue(PlaybackHttpPolicy.userAgent.startsWith("OpenStream/"))
        assertTrue(PlaybackHttpPolicy.connectTimeoutMs in 3_000..10_000)
        assertTrue(PlaybackHttpPolicy.readTimeoutMs in 8_000..30_000)
        assertTrue(PlaybackHttpPolicy.liveConnectTimeoutMs < PlaybackHttpPolicy.connectTimeoutMs)
        assertTrue(PlaybackHttpPolicy.liveReadTimeoutMs < PlaybackHttpPolicy.readTimeoutMs)
    }
}
