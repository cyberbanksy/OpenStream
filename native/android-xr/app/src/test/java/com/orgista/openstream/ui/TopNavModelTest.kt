package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TopNavModelTest {
    @Test
    fun `primary navigation stays focused on browsing`() {
        assertEquals(
            listOf(TopDestination.DISCOVER, TopDestination.LIBRARY),
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
            "Demo library",
            libraryHeaderStatus(indexing = false, hasLibraries = false, titleCount = 6, metadataEnabled = false),
        )
    }
}
