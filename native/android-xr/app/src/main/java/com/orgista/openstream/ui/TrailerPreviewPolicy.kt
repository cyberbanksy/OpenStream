package com.orgista.openstream.ui

object TrailerPreviewPolicy {
    const val START_TIMEOUT_MS = 8_000L

    private val youtubeKey = Regex("^[A-Za-z0-9_-]{6,24}$")

    fun validYoutubeKey(value: String?): Boolean = value != null && youtubeKey.matches(value)

    fun shouldAutoplay(
        youtubeKey: String?,
        online: Boolean,
        metered: Boolean,
        stageVisible: Boolean,
        motionAllowed: Boolean,
    ): Boolean = validYoutubeKey(youtubeKey) && online && !metered && stageVisible && motionAllowed

    fun shouldRevealPlayer(started: Boolean, unavailable: Boolean): Boolean =
        started && !unavailable

    fun shouldShowDetailsOverlay(expandedLayout: Boolean, trailerVisible: Boolean): Boolean =
        expandedLayout && !trailerVisible
}
