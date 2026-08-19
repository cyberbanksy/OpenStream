package com.orgista.openstream.library

import com.orgista.openstream.data.MediaItem

/** Selects a small high-value metadata working set instead of refetching every catalog row. */
object MetadataPrefetchPolicy {
    fun select(items: List<MediaItem>, limit: Int = 24): List<MediaItem> = items
        .asSequence()
        .distinctBy { it.imdbId ?: it.id }
        .filter { metadataMissing(it) }
        .take(limit.coerceAtLeast(0))
        .toList()

    private fun metadataMissing(item: MediaItem): Boolean =
        item.synopsis.isBlank() || item.posterUrl == null || item.backdropUrl == null ||
            item.runtimeMinutes <= 0 || item.rating.isBlank() || item.genres.isEmpty()
}
