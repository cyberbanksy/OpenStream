package com.orgista.openstream.offline

import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCatalogPolicyTest {
    private fun item(id: String, source: SourceKind) = MediaItem(
        id = id,
        kind = MediaKind.MOVIE,
        title = id,
        synopsis = "Synopsis",
        year = 2026,
        rating = "PG",
        runtimeMinutes = 90,
        candidates = listOf(
            MediaCandidate(id = "$id-$source", sourceName = source.name, sourceKind = source, uri = "https://example/$id" , formatLabel = "1080p"),
        ),
    )

    @Test
    fun offlineNetworkStateShowsOnlyCompletedDownloads() {
        val online = listOf(item("online", SourceKind.STREMIO))
        val offline = listOf(item("saved", SourceKind.OFFLINE))

        assertEquals(listOf("saved"), OfflineCatalogPolicy.select(false, online, offline).map { it.id })
    }

    @Test
    fun onlineCatalogKeepsOfflineRouteFirstAndRetainsOtherTitles() {
        val online = listOf(item("shared", SourceKind.STREMIO), item("online", SourceKind.STREMIO))
        val offline = listOf(item("shared", SourceKind.OFFLINE))

        val selected = OfflineCatalogPolicy.select(true, online, offline)

        assertEquals(listOf("shared", "online"), selected.map { it.id })
        assertTrue(selected.first().candidates.first().sourceKind == SourceKind.OFFLINE)
    }
}
