package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioAddonRemovalPolicyTest {
    @Test
    fun `removed bundled add-ons stay out of the visible source list`() {
        val visible = StremioAddonRemovalPolicy.visible(
            builtIns = BuiltInStremioAddons.all,
            custom = emptyList(),
            removedBuiltInIds = setOf("builtin:com.linvo.cinemeta", "builtin:tmdb-addon"),
        )

        assertTrue(visible.none { it.name == "Cinemeta" })
        assertTrue(visible.none { it.name == "The Movie Database Addon" })
        assertTrue(visible.any { it.name == "Streaming Catalogs" })
        assertTrue(visible.any { it.name == "Rotten Tomatoes" })
    }

    @Test
    fun `custom add-ons remain visible when a bundled add-on is removed`() {
        val custom = StremioAddon(
            id = "custom",
            name = "My add-on",
            transportUrl = "https://addon.example",
            version = "1",
        )

        val visible = StremioAddonRemovalPolicy.visible(
            builtIns = BuiltInStremioAddons.all,
            custom = listOf(custom),
            removedBuiltInIds = BuiltInStremioAddons.all.mapTo(mutableSetOf()) { it.id },
        )

        assertEquals(listOf(custom), visible)
    }
}
