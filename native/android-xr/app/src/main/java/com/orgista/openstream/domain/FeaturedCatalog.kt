package com.orgista.openstream.domain

/** Pure selection rules shared by the timed hero and direct card selection. */
object FeaturedCatalog {
    fun nextIndex(currentIndex: Int, size: Int): Int =
        if (size <= 0) 0 else (currentIndex.coerceAtLeast(0) + 1) % size

    fun indexForSelection(ids: List<String>, selectedId: String?): Int =
        ids.indexOf(selectedId).takeIf { it >= 0 } ?: 0
}
