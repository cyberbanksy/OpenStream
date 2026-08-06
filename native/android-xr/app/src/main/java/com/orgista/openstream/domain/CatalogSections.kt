package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind

data class CatalogSection(val title: String, val items: List<MediaItem>)

/** Builds stable, deduplicated discovery rails from local and add-on titles. */
object CatalogSections {
    fun build(catalog: List<MediaItem>): List<CatalogSection> {
        val unique = catalog.distinctBy { it.id }
        if (unique.isEmpty()) return emptyList()

        val sections = ArrayList<CatalogSection>()
        fun add(title: String, items: List<MediaItem>, minimum: Int = 1) {
            val distinct = items.distinctBy { it.id }
            if (distinct.size >= minimum) sections += CatalogSection(title, distinct)
        }

        add("Continue watching", unique.filter { it.progressPercent != null })
        add("Recommended for you", unique.filter { it.progressPercent == null }.take(24))
        add("Movies", unique.filter { it.kind == MediaKind.MOVIE })
        add("Series", unique.filter { it.kind == MediaKind.SERIES })

        val genreCounts = unique
            .flatMap { item -> item.genres.map { it.trim() }.filter(String::isNotBlank).distinct() }
            .groupingBy { it.lowercase() }
            .eachCount()
        val genreNames = unique
            .flatMap { it.genres }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .filter { (genreCounts[it.lowercase()] ?: 0) >= 2 }
            .sorted()
            .take(8)
        genreNames.forEach { genre ->
            add(
                genre,
                unique.filter { item -> item.genres.any { it.equals(genre, ignoreCase = true) } },
                minimum = 2,
            )
        }

        return sections
    }
}
