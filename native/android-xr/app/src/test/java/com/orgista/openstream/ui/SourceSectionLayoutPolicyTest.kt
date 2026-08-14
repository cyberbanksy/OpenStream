package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSectionLayoutPolicyTest {
    @Test
    fun `sources use flat informative sections instead of cards`() {
        val layout = SourceSectionLayoutPolicy.current

        assertFalse(layout.boxedRows)
        assertTrue(layout.showSectionInfo)
        assertEquals(18, layout.headingSizeSp)
        assertEquals(64, layout.minimumRowHeightDp)
    }
}
