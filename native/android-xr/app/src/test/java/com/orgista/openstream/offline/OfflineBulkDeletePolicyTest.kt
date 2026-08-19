package com.orgista.openstream.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineBulkDeletePolicyTest {
    @Test
    fun `delete all is offered only while downloads exist and removal is idle`() {
        assertTrue(OfflineBulkDeletePolicy.canStart(downloadCount = 3, removing = false))
        assertFalse(OfflineBulkDeletePolicy.canStart(downloadCount = 0, removing = false))
        assertFalse(OfflineBulkDeletePolicy.canStart(downloadCount = 3, removing = true))
    }

    @Test
    fun `confirmation is required before bulk removal`() {
        assertFalse(OfflineBulkDeletePolicy.shouldRemove(confirming = false, downloadCount = 3, removing = false))
        assertTrue(OfflineBulkDeletePolicy.shouldRemove(confirming = true, downloadCount = 3, removing = false))
        assertFalse(OfflineBulkDeletePolicy.shouldRemove(confirming = true, downloadCount = 0, removing = false))
    }
}
