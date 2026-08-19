package com.orgista.openstream.player

sealed interface LiveEdgeSeekDecision {
    data object Resume : LiveEdgeSeekDecision
    data class SeekForward(val positionMs: Long) : LiveEdgeSeekDecision
    data object Reconnect : LiveEdgeSeekDecision
}

/**
 * Returns to the live edge without ever seeking backward. Media3's default
 * live position may sit behind a viewer who is already live, so repeatedly
 * seeking to that default can replay the same segment.
 */
object LiveEdgeSeekPolicy {
    const val PLAYBACK_CUSHION_MS = 2_000L
    private const val MIN_FORWARD_SEEK_MS = 750L
    private const val WINDOW_END_CUSHION_MS = 500L

    fun decide(
        timelineIsLive: Boolean,
        currentPositionMs: Long,
        durationMs: Long?,
        currentLiveOffsetMs: Long?,
    ): LiveEdgeSeekDecision {
        if (!timelineIsLive) return LiveEdgeSeekDecision.Reconnect

        val position = currentPositionMs.coerceAtLeast(0L)
        val duration = durationMs?.takeIf { it > 0L }
        val maximumPosition = duration?.let { (it - WINDOW_END_CUSHION_MS).coerceAtLeast(0L) }
        val desiredPosition = currentLiveOffsetMs
            ?.takeIf { it >= 0L }
            ?.let { offset -> position + (offset - PLAYBACK_CUSHION_MS).coerceAtLeast(0L) }
            ?: duration?.let { (it - PLAYBACK_CUSHION_MS).coerceAtLeast(0L) }
            ?: return LiveEdgeSeekDecision.Resume
        val safePosition = maximumPosition?.let(desiredPosition::coerceAtMost) ?: desiredPosition

        return if (safePosition >= position + MIN_FORWARD_SEEK_MS) {
            LiveEdgeSeekDecision.SeekForward(safePosition)
        } else {
            LiveEdgeSeekDecision.Resume
        }
    }
}
