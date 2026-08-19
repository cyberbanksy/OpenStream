package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationTest {
    @Test
    fun `detail and player return to the last tab`() {
        assertEquals(
            BackDestination.Navigate(Screen.Library),
            backDestination(Screen.Details(testItem()), Screen.Library),
        )
        assertEquals(
            BackDestination.Navigate(Screen.Search),
            backDestination(Screen.Player(PlaybackRequest("Title", null, "https://example.test/movie")), Screen.Search),
        )
    }

    @Test
    fun `live player returns to the live guide`() {
        assertEquals(
            BackDestination.Navigate(Screen.Live),
            backDestination(
                Screen.Player(PlaybackRequest("Live", null, "https://example.test/live.m3u8", isLive = true)),
                Screen.Discover,
            ),
        )
    }

    @Test
    fun `tabs return to discover and discover exits`() {
        assertEquals(BackDestination.Navigate(Screen.Discover), backDestination(Screen.Settings, Screen.Settings))
        assertEquals(BackDestination.Exit, backDestination(Screen.Discover, Screen.Discover))
    }

    @Test
    fun `channel manager lives under sources`() {
        assertEquals(
            BackDestination.Navigate(Screen.Connections),
            backDestination(Screen.ChannelManager, Screen.Live),
        )
    }

    private fun testItem() = com.orgista.openstream.data.MediaItem(
        id = "id",
        kind = com.orgista.openstream.data.MediaKind.MOVIE,
        title = "Title",
        synopsis = "",
        year = 2026,
        rating = "PG",
        runtimeMinutes = 90,
    )
}
