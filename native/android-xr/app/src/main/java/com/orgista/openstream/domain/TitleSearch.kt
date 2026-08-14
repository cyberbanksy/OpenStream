package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.StremioCatalogMerger
import java.text.Normalizer

/** Stable title-first ranking shared by cached and provider-backed search. */
object TitleSearch {
    private val MARKS = Regex("\\p{M}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    fun index(items: List<MediaItem>): Index = Index(
        items.map { item -> Searchable(item, normalize(item.title), normalize(item.synopsis)) },
    )

    fun rank(items: List<MediaItem>, query: String): List<MediaItem> = index(items).rank(query)

    class Index internal constructor(private val entries: List<Searchable>) {
        fun rank(query: String): List<MediaItem> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return entries.map(Searchable::item)
        val terms = normalizedQuery.split(' ').filter(String::isNotBlank)
        return entries.mapNotNull { entry ->
            val title = entry.title
            val synopsis = entry.synopsis
            val score = when {
                title == normalizedQuery -> 0
                title.startsWith(normalizedQuery) -> 1
                normalizedQuery in title -> 2
                terms.all(title::contains) -> 3
                normalizedQuery in synopsis -> 4
                terms.size > 1 && terms.all(synopsis::contains) -> 5
                else -> return@mapNotNull null
            }
            Ranked(entry.item, score, title)
        }.sortedWith(
            compareBy<Ranked>({ it.score }, { it.normalizedTitle }, { it.item.year }),
        ).map(Ranked::item)
        }
    }

    fun mergeAndRank(local: List<MediaItem>, remote: List<MediaItem>, query: String): List<MediaItem> {
        val merged = StremioCatalogMerger.merge(local, remote)
        val ranked = rank(merged, query)
        val rankedIds = ranked.mapTo(HashSet()) { it.imdbId ?: it.id }
        val mergedById = merged.associateBy { it.imdbId ?: it.id }
        val providerFallbacks = remote.mapNotNull { remoteItem ->
            val id = remoteItem.imdbId ?: remoteItem.id
            mergedById[id]?.takeIf { rankedIds.add(id) }
        }
        return ranked + providerFallbacks
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()
        .replace(SEPARATORS, " ")
        .trim()

    internal data class Searchable(val item: MediaItem, val title: String, val synopsis: String)
    private data class Ranked(val item: MediaItem, val score: Int, val normalizedTitle: String)
}
