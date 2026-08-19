package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseLayoutPolicyTest {
    @Test
    fun `phone layout uses a tall hero full width copy and two column cards`() {
        val layout = BrowseLayoutPolicy.resolve(widthDp = 384f, heightDp = 640f)

        assertTrue(layout.compact)
        assertEquals(460.8f, layout.featuredHeightDp, 0.01f)
        assertEquals(1f, layout.heroContentFraction, 0.01f)
        assertEquals(34f, layout.heroTitleSp, 0.01f)
        assertEquals(148f, layout.cardWidthDp, 0.01f)
        assertEquals(2, layout.gridColumns)
        assertFalse(layout.showFormatInHero)
    }

    @Test
    fun `tablet layout preserves the cinematic sixteen by nine stage and large cards`() {
        val layout = BrowseLayoutPolicy.resolve(widthDp = 1280f, heightDp = 800f)

        assertFalse(layout.compact)
        assertEquals(720f, layout.featuredHeightDp, 0.01f)
        assertEquals(0.6f, layout.heroContentFraction, 0.01f)
        assertEquals(46f, layout.heroTitleSp, 0.01f)
        assertEquals(184f, layout.cardWidthDp, 0.01f)
        assertEquals(0, layout.gridColumns)
        assertTrue(layout.showFormatInHero)
    }

    @Test
    fun `rotating a phone keeps compact navigation and cards`() {
        val layout = BrowseLayoutPolicy.resolve(widthDp = 640f, heightDp = 384f)

        assertTrue(layout.compact)
        assertEquals(312f, layout.featuredHeightDp, 0.01f)
        assertEquals(28f, layout.heroTitleSp, 0.01f)
        assertEquals(2, layout.gridColumns)
        assertFalse(layout.showFormatInHero)
    }

    @Test
    fun `featured rotation cue stays centered compact and above system navigation`() {
        val phone = BrowseLayoutPolicy.featuredProgressStyle(compact = true)
        val tablet = BrowseLayoutPolicy.featuredProgressStyle(compact = false)

        assertEquals(48f, phone.widthDp, 0.01f)
        assertEquals(60f, tablet.widthDp, 0.01f)
        assertEquals(44f, phone.bottomPaddingDp, 0.01f)
        assertEquals(44f, tablet.bottomPaddingDp, 0.01f)
        assertEquals(1.5f, tablet.heightDp, 0.01f)
        assertEquals(0.14f, tablet.trackAlpha, 0.01f)
        assertEquals(0.68f, tablet.fillAlpha, 0.01f)
    }
}
