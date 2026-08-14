package com.orgista.openstream.player

enum class LiveVideoFreezeRecoveryAction {
    NONE,
    RESTART_ROUTE,
    ADVANCE_ROUTE,
}

object LiveVideoFreezeRecoveryPolicy {
    const val STALL_TIMEOUT_MS = 15_000L
    const val STABLE_PLAYBACK_RESET_MS = 30_000L

    /**
     * A live stream is considered frozen only when Media3 still reports active
     * playback and its position advances while a selected video track has not
     * submitted a frame for the full timeout. This deliberately excludes
     * buffering, paused, audio-only, and on-demand playback.
     */
    fun action(
        isLive: Boolean,
        isPlaying: Boolean,
        hasSelectedVideo: Boolean,
        playbackPositionAdvanced: Boolean,
        millisSinceLastVideoFrame: Long,
        recoveryAttempts: Int,
    ): LiveVideoFreezeRecoveryAction {
        if (!isLive || !isPlaying || !hasSelectedVideo || !playbackPositionAdvanced) {
            return LiveVideoFreezeRecoveryAction.NONE
        }
        if (millisSinceLastVideoFrame < STALL_TIMEOUT_MS) {
            return LiveVideoFreezeRecoveryAction.NONE
        }
        return if (recoveryAttempts == 0) {
            LiveVideoFreezeRecoveryAction.RESTART_ROUTE
        } else {
            LiveVideoFreezeRecoveryAction.ADVANCE_ROUTE
        }
    }
}
