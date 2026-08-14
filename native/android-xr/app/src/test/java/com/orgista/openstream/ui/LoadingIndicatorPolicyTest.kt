package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingIndicatorPolicyTest {
    @Test
    fun `all loader roles use compact round indicators`() {
        LoadingIndicatorRole.entries.forEach { role ->
            val metrics = LoadingIndicatorPolicy.metrics(role)
            assertTrue(metrics.diameterDp in 14..32)
            assertTrue(metrics.strokeWidthDp in 2..3)
            assertTrue(metrics.roundStroke)
        }
    }

    @Test
    fun `content and playback loaders are centered`() {
        assertTrue(LoadingIndicatorPolicy.metrics(LoadingIndicatorRole.CONTENT).centerInContainer)
        assertTrue(LoadingIndicatorPolicy.metrics(LoadingIndicatorRole.PLAYBACK).centerInContainer)
        assertEquals(28, LoadingIndicatorPolicy.metrics(LoadingIndicatorRole.PLAYBACK).diameterDp)
    }
}
