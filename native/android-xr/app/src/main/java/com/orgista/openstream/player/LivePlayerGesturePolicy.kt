package com.orgista.openstream.player

import kotlin.math.abs

object LivePlayerGesturePolicy {
    private const val MIN_HORIZONTAL_FRACTION = 0.08f
    private const val MAX_VERTICAL_TO_HORIZONTAL_RATIO = 0.70f

    /**
     * A deliberate swipe anywhere across Live TV changes channel without
     * adding permanent previous/next controls over the picture. Right selects
     * the next channel and left selects the previous channel.
     */
    fun channelDirection(
        startX: Float,
        endX: Float,
        deltaY: Float,
        screenWidthPx: Float,
    ): Int {
        if (screenWidthPx <= 0f) return 0
        val deltaX = endX - startX
        val horizontalDistance = abs(deltaX)
        if (horizontalDistance < screenWidthPx * MIN_HORIZONTAL_FRACTION) return 0
        if (abs(deltaY) > horizontalDistance * MAX_VERTICAL_TO_HORIZONTAL_RATIO) return 0
        return if (deltaX > 0f) 1 else -1
    }
}
