package com.orgista.openstream.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FeaturedCatalogTest {
    @Test
    fun `featured selection wraps and follows a selected title`() {
        assertEquals(0, FeaturedCatalog.nextIndex(currentIndex = 2, size = 3))
        assertEquals(2, FeaturedCatalog.indexForSelection(listOf("a", "b", "c"), "c"))
    }

    @Test
    fun `featured selection is safe for an empty or replaced catalog`() {
        assertEquals(0, FeaturedCatalog.nextIndex(currentIndex = 8, size = 0))
        assertEquals(0, FeaturedCatalog.indexForSelection(listOf("new"), "removed"))
    }
}
