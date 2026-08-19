package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverFeedPreferencesTest {
    @Test
    fun `default feed keeps up next first and every group visible`() {
        val preferences = DiscoverFeedPreferences.DEFAULT

        assertEquals(
            listOf(
                DiscoverFeedGroup.UP_NEXT,
                DiscoverFeedGroup.LIBRARY,
                DiscoverFeedGroup.ADD_ONS,
                DiscoverFeedGroup.YOUTUBE,
            ),
            preferences.order,
        )
        assertTrue(DiscoverFeedGroup.YOUTUBE in preferences.visibleGroups)
    }

    @Test
    fun `codec removes unknown duplicates and restores missing groups`() {
        val decoded = DiscoverFeedPreferencesCodec.decode(
            """{"order":["YOUTUBE","UNKNOWN","YOUTUBE"],"hidden":["ADD_ONS","UNKNOWN"]}""",
        )

        assertEquals(DiscoverFeedGroup.YOUTUBE, decoded.order.first())
        assertEquals(DiscoverFeedGroup.entries.toSet(), decoded.order.toSet())
        assertFalse(DiscoverFeedGroup.ADD_ONS in decoded.visibleGroups)
        assertTrue(DiscoverFeedGroup.LIBRARY in decoded.visibleGroups)
    }

    @Test
    fun `groups can be hidden and reordered without losing the other choices`() {
        val updated = DiscoverFeedPreferences.DEFAULT
            .withVisibility(DiscoverFeedGroup.YOUTUBE, false)
            .move(DiscoverFeedGroup.YOUTUBE, -1)

        assertFalse(DiscoverFeedGroup.YOUTUBE in updated.visibleGroups)
        assertEquals(DiscoverFeedGroup.YOUTUBE, updated.order[2])
        assertEquals(updated, DiscoverFeedPreferencesCodec.decode(DiscoverFeedPreferencesCodec.encode(updated)))
    }
}
