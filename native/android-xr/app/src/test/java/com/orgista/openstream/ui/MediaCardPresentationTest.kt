package com.orgista.openstream.ui

import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCardPresentationTest {
    private fun item(subtitle: String?, candidate: MediaCandidate? = null) = MediaItem(
        id = "tt1234567",
        kind = MediaKind.MOVIE,
        title = "Title",
        subtitle = subtitle,
        synopsis = "",
        year = 2025,
        rating = "PG",
        runtimeMinutes = 100,
        imdbId = "tt1234567",
        candidates = listOfNotNull(candidate),
    )

    @Test
    fun `remote provider names are presented as stream`() {
        assertEquals("Stream", MediaCardPresentation.subtitle(item("Cinemeta")))
    }

    @Test
    fun `local and offline cards identify device relationship`() {
        val local = MediaCandidate("1", "NAS", SourceKind.NAS, "smb://host/movie.mkv", "4K")
        val offline = MediaCandidate("2", "Device", SourceKind.OFFLINE, "https://example/movie", "HD", offlineDownloadId = "d")
        assertEquals("Local", MediaCardPresentation.subtitle(item("NAS", local)))
        assertEquals("Offline", MediaCardPresentation.subtitle(item("Available offline", offline)))
    }

    @Test
    fun `episode summaries remain useful`() {
        assertEquals("12 episodes · 2 seasons", MediaCardPresentation.subtitle(item("12 episodes · 2 seasons")))
    }
}
