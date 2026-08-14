package com.orgista.openstream.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStoragePolicyTest {
    @Test
    fun `sd card preference falls back to internal storage when no card is mounted`() {
        assertEquals(
            OfflineStorageLocation.INTERNAL,
            OfflineStoragePolicy.effectiveLocation(OfflineStorageLocation.SD_CARD, removableAvailable = false),
        )
    }

    @Test
    fun `sd card preference is honored when removable storage is mounted`() {
        assertEquals(
            OfflineStorageLocation.SD_CARD,
            OfflineStoragePolicy.effectiveLocation(OfflineStorageLocation.SD_CARD, removableAvailable = true),
        )
    }

    @Test
    fun `storage can only change before downloads exist`() {
        assertTrue(OfflineStoragePolicy.canChange(downloadCount = 0))
        assertFalse(OfflineStoragePolicy.canChange(downloadCount = 1))
    }
}
