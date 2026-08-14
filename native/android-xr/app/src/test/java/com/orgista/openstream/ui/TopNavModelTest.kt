package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopNavModelTest {
    @Test
    fun `phone widths use the compact two row navigation`() {
        assertTrue(TopNavLayoutPolicy.isCompact(widthDp = 384, heightDp = 640))
        assertTrue(TopNavLayoutPolicy.isCompact(widthDp = 640, heightDp = 384))
        assertFalse(TopNavLayoutPolicy.isCompact(widthDp = 1280, heightDp = 800))
    }

    @Test
    fun `wide brand receives baseline compensation beside navigation tabs`() {
        assertEquals(7, TopNavLayoutPolicy.brandBottomPaddingDp(compact = false))
        assertEquals(0, TopNavLayoutPolicy.brandBottomPaddingDp(compact = true))
    }

    @Test
    fun `app chrome omits duplicate system connectivity telemetry`() {
        assertFalse(TopNavLayoutPolicy.showConnectivityTelemetry)
        assertEquals(TopDestination.DISCOVER, TopNavLayoutPolicy.brandDestination)
    }

    @Test
    fun `primary navigation stays focused on browsing`() {
        assertEquals(
            listOf(TopDestination.DISCOVER, TopDestination.LIBRARY, TopDestination.LIVE),
            primaryTopDestinations(),
        )
    }

    @Test
    fun `overflow exposes sources and settings outside kids mode`() {
        assertEquals(
            listOf(TopDestination.SOURCES, TopDestination.SETTINGS),
            overflowTopDestinations(kidsMode = false),
        )
        assertEquals(
            listOf(TopDestination.SETTINGS),
            overflowTopDestinations(kidsMode = true),
        )
    }

    @Test
    fun `library status prioritizes active scan and explains missing artwork setup`() {
        assertEquals(
            "Scanning library…",
            libraryHeaderStatus(indexing = true, hasLibraries = true, titleCount = 389, metadataEnabled = false),
        )
        assertEquals(
            "389 titles · artwork needs TMDB",
            libraryHeaderStatus(indexing = false, hasLibraries = true, titleCount = 389, metadataEnabled = false),
        )
        assertEquals(
            "389 titles",
            libraryHeaderStatus(indexing = false, hasLibraries = true, titleCount = 389, metadataEnabled = true),
        )
        assertEquals(
            "Add a source",
            libraryHeaderStatus(indexing = false, hasLibraries = false, titleCount = 0, metadataEnabled = false),
        )
    }
}
