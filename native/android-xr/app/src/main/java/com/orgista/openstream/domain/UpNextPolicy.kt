package com.orgista.openstream.domain

import com.orgista.openstream.data.AddonCatalogSection
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind

/** Apple-style next-intent rail: resume first, then fresh channels and likely unwatched titles. */
object UpNextPolicy {
    fun select(
        rankedCatalog: List<MediaItem>,
        youtubeSections: List<AddonCatalogSection>,
        history: Map<String, ViewingSignal>,
        limit: Int = 20,
    ): List<MediaItem> {
        val maximum = limit.coerceIn(1, 40)
        val selected = LinkedHashMap<String, MediaItem>()
        fun add(item: MediaItem) {
            if (selected.size < maximum) selected.putIfAbsent(item.imdbId ?: item.id, item)
        }

        rankedCatalog.asSequence()
            .filter { it.progressPercent != null }
            .take(6)
            .forEach(::add)
        youtubeSections.asSequence()
            .mapNotNull { section -> section.items.firstOrNull { it.kind == MediaKind.VIDEO } }
            .take(8)
            .forEach(::add)
        rankedCatalog.asSequence()
            .filter { it.kind != MediaKind.VIDEO && history[it.id]?.completed != true }
            .forEach(::add)
        youtubeSections.asSequence().flatMap { it.items.asSequence() }.forEach(::add)
        return selected.values.take(maximum)
    }
}
