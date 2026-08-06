package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
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
}
