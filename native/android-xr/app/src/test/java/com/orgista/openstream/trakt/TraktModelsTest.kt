package com.orgista.openstream.trakt

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TraktModelsTest {
    @Test
    fun `movie scrobble uses imdb identity and clamps progress`() {
        val item = MediaItem(
            id = "tt1234567",
            kind = MediaKind.MOVIE,
            title = "Film",
            synopsis = "",
            year = 2026,
            rating = "",
            runtimeMinutes = 100,
            imdbId = "tt1234567",
        )

        val identity = TraktMediaIdentity.from(item)
        val payload = TraktPayloads.scrobble(identity!!, 140.0)

        assertEquals("tt1234567", payload.getJSONObject("movie").getJSONObject("ids").getString("imdb"))
        assertEquals(100.0, payload.getDouble("progress"), 0.0)
    }

    @Test
    fun `series episode notation becomes an episode identity`() {
        val item = MediaItem(
            id = "episode-1",
            kind = MediaKind.SERIES,
            title = "A Show",
            subtitle = "S2 E6",
            synopsis = "",
            year = 2024,
            rating = "",
            runtimeMinutes = 48,
            imdbId = "tt7654321",
        )

        val identity = TraktMediaIdentity.from(item)
        val payload = TraktPayloads.scrobble(identity!!, 50.0)

        assertNotNull(payload.optJSONObject("show"))
        assertEquals(2, payload.getJSONObject("episode").getInt("season"))
        assertEquals(6, payload.getJSONObject("episode").getInt("number"))
    }

    @Test
    fun `title without imdb identity is not synced`() {
        val item = MediaItem("local", MediaKind.MOVIE, "Local", synopsis = "", year = 0, rating = "", runtimeMinutes = 0)

        assertNull(TraktMediaIdentity.from(item))
    }

    @Test
    fun `series without an episode number is not incorrectly scrobbled as a movie`() {
        val item = MediaItem(
            "show",
            MediaKind.SERIES,
            "A Show",
            subtitle = "Cinemeta",
            synopsis = "",
            year = 2024,
            rating = "",
            runtimeMinutes = 48,
            imdbId = "tt7654321",
        )

        assertNull(TraktMediaIdentity.from(item))
    }
}
