package com.orgista.openstream.player

import com.orgista.openstream.data.UserAgents

object PlayerLoadingUiPolicy {
    /** PlayerView sits below the branded Compose preparation panel, so its spinner must stay off. */
    const val showNativePlayerSpinner = false
    const val overlayWidthFraction = 0.88f
    const val overlayMaxWidthDp = 340
    const val horizontalPaddingDp = 18
    const val verticalPaddingDp = 12
    const val spinnerSizeDp = 28
    const val headingSizeSp = 16

    fun showPreparationOverlay(
        statusVisible: Boolean,
        statusText: String,
        hasTerminalError: Boolean,
        playWhenReady: Boolean = true,
    ): Boolean = playWhenReady && statusVisible && statusText.isNotBlank() && !hasTerminalError

    fun heading(
        isLive: Boolean,
        isRemote: Boolean,
        isBuffering: Boolean,
        livePlaybackStarted: Boolean = false,
    ): String = when {
        isLive -> "Loading"
        isRemote && isBuffering -> "Optimizing for your network"
        else -> "Preparing playback"
    }
}

object PlaybackHttpPolicy {
    val userAgent: String = UserAgents.live
    const val connectTimeoutMs = 8_000
    const val readTimeoutMs = 15_000
    const val liveConnectTimeoutMs = 4_000
    const val liveReadTimeoutMs = 8_000
    const val followCrossProtocolRedirects = true
}
