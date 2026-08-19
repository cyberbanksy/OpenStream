package com.orgista.openstream.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackRoutePresentationTest {
    @Test
    fun `primary route keeps measured quality status`() {
        assertEquals(
            "20 Mbps · Dolby Vision selected",
            PlaybackRoutePresentation.status(
                primaryStatus = "20 Mbps · Dolby Vision selected",
                activeRouteIndex = 0,
                routeCount = 12,
                selectedTrackLabels = listOf("Dolby Vision"),
            ),
        )
    }

    @Test
    fun `fallback status does not retain the primary dolby claim`() {
        val status = PlaybackRoutePresentation.status(
            primaryStatus = "20 Mbps · Dolby Vision selected",
            activeRouteIndex = 3,
            routeCount = 12,
            selectedTrackLabels = emptyList(),
        )

        assertEquals("Alternative 4/12", status)
        assertFalse(status.contains("Dolby Vision"))
    }

    @Test
    fun `fallback reports only formats confirmed from selected tracks`() {
        assertEquals(
            "Alternative 2/5 · HDR10 · Dolby Atmos",
            PlaybackRoutePresentation.status(
                primaryStatus = "Dolby Vision selected",
                activeRouteIndex = 1,
                routeCount = 5,
                selectedTrackLabels = listOf("HDR10", "Dolby Atmos"),
            ),
        )
    }

    @Test
    fun `compact player chrome describes resolution and route without provider details`() {
        assertEquals("4K Stream", PlaybackRoutePresentation.compactLabel(2160, "https://media.example/title", false))
        assertEquals("HD Local", PlaybackRoutePresentation.compactLabel(1080, "smb://nas/title", false))
        assertEquals("HD Offline", PlaybackRoutePresentation.compactLabel(1080, "https://media.example/title", true))
    }
}
