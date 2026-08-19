package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPresentationPolicyTest {
    @Test
    fun `motion launch follows the supplied five second scene timing`() {
        assertEquals(900, LaunchPresentationPolicy.signalMillis)
        assertEquals(1_300, LaunchPresentationPolicy.confluenceMillis)
        assertEquals(1_100, LaunchPresentationPolicy.lockupMillis)
        assertEquals(1_700, LaunchPresentationPolicy.discoverMillis)
        assertEquals(5_000L, LaunchPresentationPolicy.durationMillis(animatorsEnabled = true))
    }

    @Test
    fun `reduced motion avoids the five second flourish`() {
        assertEquals(220L, LaunchPresentationPolicy.durationMillis(animatorsEnabled = false))
    }

    @Test
    fun `scene progress is clamped and discover begins after lockup`() {
        assertEquals(3_300, LaunchPresentationPolicy.discoverStartMillis)
        assertEquals(0f, LaunchPresentationPolicy.progress(3_299, 3_300, 800))
        assertEquals(0.5f, LaunchPresentationPolicy.progress(3_700, 3_300, 800))
        assertEquals(1f, LaunchPresentationPolicy.progress(4_200, 3_300, 800))
    }

    @Test
    fun `brand geometry preserves the supplied stream proportions`() {
        assertEquals(96f, OpenStreamBrandGeometry.viewBox)
        assertEquals(22f, OpenStreamBrandGeometry.startX)
        assertEquals(12.5f, OpenStreamBrandGeometry.strokeWidth)
        assertEquals(listOf(31f, 48.5f, 66f), OpenStreamBrandGeometry.bars.map { it.y })
        assertEquals(listOf(40f, 56f, 40f), OpenStreamBrandGeometry.bars.map { it.length })
    }

    @Test
    fun `launcher mark is substantially smaller than the adaptive icon safe zone`() {
        assertEquals(36f, LauncherIconPresentationPolicy.foregroundInsetDp)
        assertTrue(LauncherIconPresentationPolicy.renderedMarkWidthDp() <= 26f)
        assertTrue(LauncherIconPresentationPolicy.renderedMarkHeightDp() <= 18f)
    }
}
