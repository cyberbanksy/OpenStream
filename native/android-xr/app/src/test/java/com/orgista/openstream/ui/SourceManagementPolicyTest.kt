package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagementPolicyTest {
    @Test
    fun `section actions use concise source specific copy`() {
        assertEquals("Delete libraries", SourceManagementPolicy.deleteActionLabel(SourceCollection.LIBRARIES, false))
        assertEquals("Delete shares", SourceManagementPolicy.deleteActionLabel(SourceCollection.NAS_SHARES, false))
        assertEquals("Delete channels", SourceManagementPolicy.deleteActionLabel(SourceCollection.YOUTUBE, false))
        assertEquals("Delete add-ons", SourceManagementPolicy.deleteActionLabel(SourceCollection.ADD_ONS, false))
        assertEquals("Delete source", SourceManagementPolicy.deleteActionLabel(SourceCollection.LIVE_TV, false))
        assertEquals("Done", SourceManagementPolicy.deleteActionLabel(SourceCollection.ADD_ONS, true))
    }

    @Test
    fun `delete action only appears for a populated source collection`() {
        assertFalse(SourceManagementPolicy.showDeleteAction(0))
        assertTrue(SourceManagementPolicy.showDeleteAction(1))
    }

    @Test
    fun `only the active collection exposes destructive controls`() {
        assertTrue(SourceManagementPolicy.showRemoveControl(SourceCollection.NAS_SHARES, SourceCollection.NAS_SHARES))
        assertFalse(SourceManagementPolicy.showRemoveControl(SourceCollection.NAS_SHARES, SourceCollection.YOUTUBE))
        assertFalse(SourceManagementPolicy.showRemoveControl(SourceCollection.NAS_SHARES, null))
    }

    @Test
    fun `delete mode toggles off or switches directly between collections`() {
        assertEquals(
            SourceCollection.ADD_ONS,
            SourceManagementPolicy.nextDeleteCollection(SourceCollection.ADD_ONS, null),
        )
        assertEquals(
            null,
            SourceManagementPolicy.nextDeleteCollection(SourceCollection.ADD_ONS, SourceCollection.ADD_ONS),
        )
        assertEquals(
            SourceCollection.YOUTUBE,
            SourceManagementPolicy.nextDeleteCollection(SourceCollection.YOUTUBE, SourceCollection.ADD_ONS),
        )
    }
}
