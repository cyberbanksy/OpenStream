package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsSectionLayoutPolicyTest {
    @Test
    fun `settings groups use headings and space instead of divider lines`() {
        val layout = SettingsSectionLayoutPolicy.current

        assertFalse(layout.showDivider)
        assertEquals(18, layout.headingSizeSp)
        assertEquals(18, layout.topPaddingDp)
        assertEquals(8, layout.bottomPaddingDp)
    }
}
