package com.orgista.openstream.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadUiPolicyTest {
    @Test
    fun `completed downloads use a check and cannot be queued again`() {
        assertEquals(
            OfflineDownloadVisual.CHECK,
            OfflineDownloadUiPolicy.visual(completed = true, active = false, preparing = false),
        )
        assertFalse(OfflineDownloadUiPolicy.canQueue(completed = true, active = false, preparing = false))
    }

    @Test
    fun `available downloads use one download action`() {
        assertEquals(
            OfflineDownloadVisual.DOWNLOAD,
            OfflineDownloadUiPolicy.visual(completed = false, active = false, preparing = false),
        )
        assertTrue(OfflineDownloadUiPolicy.canQueue(completed = false, active = false, preparing = false))
    }

    @Test
    fun `queued and preparing downloads show progress without a second action`() {
        assertEquals(
            OfflineDownloadVisual.PROGRESS,
            OfflineDownloadUiPolicy.visual(completed = false, active = true, preparing = false),
        )
        assertEquals(
            OfflineDownloadVisual.PROGRESS,
            OfflineDownloadUiPolicy.visual(completed = false, active = false, preparing = true),
        )
        assertFalse(OfflineDownloadUiPolicy.canQueue(completed = false, active = true, preparing = false))
    }
}
