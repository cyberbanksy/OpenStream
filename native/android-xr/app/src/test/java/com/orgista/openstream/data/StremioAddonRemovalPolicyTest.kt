package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StremioAddonRemovalPolicyTest {
    private fun item(id: String) = MediaItem(
        id = id,
        kind = MediaKind.MOVIE,
        title = id,
        synopsis = "",
        year = 2026,
        rating = "",
        runtimeMinutes = 0,
    )

    @Test
    fun `fresh installs have no compiled discovery add-ons`() {
        assertEquals(emptyList<StremioAddon>(), StremioAddonRemovalPolicy.visible(emptyList()))
    }

    @Test
    fun `only user configured add-ons remain visible and duplicate manifests collapse`() {
        val configured = StremioAddon(
            id = "custom",
            name = "My add-on",
            transportUrl = "https://addon.example",
            version = "1",
        )

        val visible = StremioAddonRemovalPolicy.visible(listOf(configured, configured.copy(id = "duplicate")))

        assertEquals(listOf(configured), visible)
    }

    @Test
    fun `cached rows from removed defaults and book catalogs are discarded`() {
        val movie = item("movie")
        val stale = item("stale")
        val cache = CachedStremioCatalog(
            updatedAtMs = 1,
            items = listOf(movie, stale),
            sections = listOf(
                AddonCatalogSection("configured", "Mine", "movie:popular", "Movies", listOf(movie)),
                AddonCatalogSection("configured", "Mine", "book:popular", "Books", listOf(stale)),
                AddonCatalogSection("compiled-default", "Old default", "movie:top", "Popular", listOf(stale)),
            ),
        )
        val configured = StremioAddon(
            id = "mine",
            manifestId = "configured",
            name = "Mine",
            transportUrl = "https://addon.example",
            version = "1",
            catalogs = listOf(
                StremioCatalog("movie", "popular", "Movies"),
                StremioCatalog("book", "popular", "Books"),
            ),
        )

        val retained = ConfiguredStremioCatalogPolicy.retain(cache, listOf(configured))

        assertEquals(listOf("movie"), retained?.items?.map { it.id })
        assertEquals(listOf("movie:popular"), retained?.sections?.map { it.catalogId })
        assertEquals(null, ConfiguredStremioCatalogPolicy.retain(cache, emptyList()))
    }
}
