package com.orgista.openstream.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePlayerGesturePolicyTest {
    @Test
    fun `horizontal swipes anywhere in the player change channels`() {
        assertEquals(
            1,
            LivePlayerGesturePolicy.channelDirection(
                startX = 620f,
                endX = 1_280f,
                deltaY = 30f,
                screenWidthPx = 2_560f,
            ),
        )
        assertEquals(
            -1,
            LivePlayerGesturePolicy.channelDirection(
                startX = 1_940f,
                endX = 1_280f,
                deltaY = -20f,
                screenWidthPx = 2_560f,
            ),
        )
    }

    @Test
    fun `short and mostly vertical swipes do not change channels`() {
        assertEquals(0, LivePlayerGesturePolicy.channelDirection(900f, 1_040f, 0f, 2_560f))
        assertEquals(0, LivePlayerGesturePolicy.channelDirection(900f, 1_700f, 900f, 2_560f))
        assertEquals(0, LivePlayerGesturePolicy.channelDirection(12f, 12f, 0f, 2_560f))
        assertEquals(0, LivePlayerGesturePolicy.channelDirection(12f, 1_220f, 0f, 0f))
    }

    @Test
    fun `natural tablet swipe does not require crossing most of the screen`() {
        assertEquals(1, LivePlayerGesturePolicy.channelDirection(1_000f, 1_260f, 20f, 2_560f))
        assertEquals(-1, LivePlayerGesturePolicy.channelDirection(1_260f, 1_000f, -20f, 2_560f))
    }
}
