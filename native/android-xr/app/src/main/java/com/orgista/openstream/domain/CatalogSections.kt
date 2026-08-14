package com.orgista.openstream.domain

import com.orgista.openstream.data.AddonCatalogSection
import com.orgista.openstream.data.DiscoverFeedGroup
import com.orgista.openstream.data.DiscoverFeedPreferences
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind

data class CatalogSection(
    val title: String,
    val items: List<MediaItem>,
    val addonName: String? = null,
)

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
        add("Recommended for you", unique.filter { it.progressPercent == null && it.kind != MediaKind.VIDEO }.take(24))
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

    /**
     * Keeps provider/category provenance for discovery. The item instances are
     * replaced with their merged, policy-filtered versions from [catalog], so
     * playback progress and local/offline routes still work inside every rail.
     */
    fun buildDiscover(
        catalog: List<MediaItem>,
        addonCatalogs: List<AddonCatalogSection>,
        feedPreferences: DiscoverFeedPreferences = DiscoverFeedPreferences.DEFAULT,
        upNextItems: List<MediaItem> = emptyList(),
    ): List<CatalogSection> {
        if (addonCatalogs.isEmpty() && upNextItems.isEmpty() && feedPreferences == DiscoverFeedPreferences.DEFAULT) {
            return build(catalog)
        }
        val unique = catalog.distinctBy(::identity)
        if (unique.isEmpty()) return emptyList()
        val visible = unique.associateBy(::identity)
        val providerIdentities = addonCatalogs.flatMap { section -> section.items.map(::identity) }.toSet()

        fun add(target: MutableList<CatalogSection>, title: String, values: List<MediaItem>, addonName: String? = null) {
            val items = values.distinctBy(::identity)
            if (items.isNotEmpty()) target += CatalogSection(title, items, addonName)
        }

        val grouped = DiscoverFeedGroup.entries.associateWith { ArrayList<CatalogSection>() }
        add(grouped.getValue(DiscoverFeedGroup.UP_NEXT), "Up Next", upNextItems.mapNotNull { visible[identity(it)] })

        val personal = unique.filter { identity(it) !in providerIdentities || it.candidates.isNotEmpty() }
        val librarySections = grouped.getValue(DiscoverFeedGroup.LIBRARY)
        add(librarySections, "Continue watching", personal.filter { it.progressPercent != null })
        add(librarySections, "Recommended for you", personal.filter { it.progressPercent == null && it.kind != MediaKind.VIDEO }.take(24))
        add(librarySections, "Your movies", personal.filter { it.kind == MediaKind.MOVIE })
        add(librarySections, "Your series", personal.filter { it.kind == MediaKind.SERIES })

        addonCatalogs.forEach { catalogSection ->
            add(
                target = grouped.getValue(
                    if (catalogSection.addonId.startsWith("youtube:")) {
                        DiscoverFeedGroup.YOUTUBE
                    } else {
                        DiscoverFeedGroup.ADD_ONS
                    },
                ),
                title = catalogSection.catalogName,
                values = catalogSection.items.mapNotNull { visible[identity(it)] },
                addonName = catalogSection.addonName,
            )
        }
        return feedPreferences.order
            .asSequence()
            .filter(feedPreferences.visibleGroups::contains)
            .flatMap { grouped.getValue(it).asSequence() }
            .toList()
    }

    private fun identity(item: MediaItem): String = item.imdbId ?: item.id
}
