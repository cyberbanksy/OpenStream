package com.orgista.openstream.domain

import com.orgista.openstream.data.AddonCatalogSection
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.DiscoverFeedGroup
import com.orgista.openstream.data.DiscoverFeedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSectionsTest {
    private fun item(id: String, kind: MediaKind, genres: List<String> = emptyList(), progress: Int? = null) = MediaItem(
        id = id,
        kind = kind,
        title = id,
        synopsis = "Synopsis",
        year = 2026,
        rating = "PG",
        runtimeMinutes = 100,
        genres = genres,
        progressPercent = progress,
    )

    @Test
    fun `discover is split into useful rails rather than one all section`() {
        val sections = CatalogSections.build(
            listOf(
                item("resume", MediaKind.MOVIE, listOf("Drama"), progress = 42),
                item("space", MediaKind.MOVIE, listOf("Science Fiction")),
                item("show", MediaKind.SERIES, listOf("Drama")),
            ),
        )

        assertEquals("Continue watching", sections.first().title)
        assertTrue(sections.any { it.title == "Movies" })
        assertTrue(sections.any { it.title == "Series" })
        assertTrue(sections.any { it.title == "Drama" })
        assertFalse(sections.any { it.title == "All titles" })
        assertTrue(sections.all { it.items.isNotEmpty() })
    }

    @Test
    fun `sections do not repeat duplicate media ids`() {
        val duplicate = item("same", MediaKind.MOVIE, listOf("Drama"))
        val sections = CatalogSections.build(listOf(duplicate, duplicate))

        assertTrue(sections.all { section -> section.items.map { it.id }.distinct().size == section.items.size })
    }

    @Test
    fun `discover keeps each addon and category as its own rail`() {
        val shared = item("shared", MediaKind.MOVIE)
        val sections = CatalogSections.buildDiscover(
            catalog = listOf(shared, item("show", MediaKind.SERIES)),
            addonCatalogs = listOf(
                AddonCatalogSection("rt", "Rotten Tomatoes", "fresh", "Certified Fresh", listOf(shared)),
                AddonCatalogSection("cinemeta", "Cinemeta", "popular", "Popular movies", listOf(shared)),
            ),
        )

        val addonSections = sections.filter { it.addonName != null }
        assertEquals(listOf("Rotten Tomatoes", "Cinemeta"), addonSections.map { it.addonName })
        assertEquals(listOf("Certified Fresh", "Popular movies"), addonSections.map { it.title })
        assertTrue(addonSections.all { it.items.single().id == "shared" })
    }

    @Test
    fun `addon rails exclude titles hidden from the visible catalog`() {
        val visible = item("visible", MediaKind.MOVIE)
        val hidden = item("hidden", MediaKind.MOVIE)
        val sections = CatalogSections.buildDiscover(
            catalog = listOf(visible),
            addonCatalogs = listOf(
                AddonCatalogSection("rt", "Rotten Tomatoes", "fresh", "Certified Fresh", listOf(visible, hidden)),
            ),
        )

        assertEquals(listOf("visible"), sections.single { it.addonName == "Rotten Tomatoes" }.items.map { it.id })
    }

    @Test
    fun `discover feed preferences hide and order whole source groups`() {
        val movie = item("movie", MediaKind.MOVIE)
        val local = item("local", MediaKind.MOVIE)
        val youtube = item("youtube:aaaaaaaaaaa", MediaKind.VIDEO)
        val sections = CatalogSections.buildDiscover(
            catalog = listOf(movie, local, youtube),
            addonCatalogs = listOf(
                AddonCatalogSection("rt", "Rotten Tomatoes", "fresh", "Certified Fresh", listOf(movie)),
                AddonCatalogSection("youtube:one", "YouTube", "one", "Channel One", listOf(youtube)),
            ),
            feedPreferences = DiscoverFeedPreferences(
                order = listOf(
                    DiscoverFeedGroup.YOUTUBE,
                    DiscoverFeedGroup.UP_NEXT,
                    DiscoverFeedGroup.ADD_ONS,
                    DiscoverFeedGroup.LIBRARY,
                ),
                visibleGroups = setOf(
                    DiscoverFeedGroup.YOUTUBE,
                    DiscoverFeedGroup.UP_NEXT,
                    DiscoverFeedGroup.LIBRARY,
                ),
            ),
            upNextItems = listOf(youtube, movie),
        )

        assertEquals("YouTube", sections.first().addonName)
        assertEquals("Up Next", sections[1].title)
        assertFalse(sections.any { it.addonName == "Rotten Tomatoes" })
        assertTrue(sections.any { it.title == "Your movies" })
    }
}
