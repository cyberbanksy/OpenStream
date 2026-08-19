package com.orgista.openstream.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPreviewPolicyTest {
    @Test
    fun `muted preview autoplays only when visible online and unmetered`() {
        assertTrue(
            TrailerPreviewPolicy.shouldAutoplay(
                youtubeKey = "safe_key-1",
                online = true,
                metered = false,
                stageVisible = true,
                motionAllowed = true,
            ),
        )
        assertFalse(TrailerPreviewPolicy.shouldAutoplay("safe_key-1", true, true, true, true))
        assertFalse(TrailerPreviewPolicy.shouldAutoplay("safe_key-1", false, false, true, true))
        assertFalse(TrailerPreviewPolicy.shouldAutoplay("safe_key-1", true, false, false, true))
        assertFalse(TrailerPreviewPolicy.shouldAutoplay("safe_key-1", true, false, true, false))
    }

    @Test
    fun `preview rejects ids that could escape the youtube embed URL`() {
        assertFalse(TrailerPreviewPolicy.validYoutubeKey("bad/key"))
        assertFalse(TrailerPreviewPolicy.validYoutubeKey("https://youtu.be/video"))
        assertTrue(TrailerPreviewPolicy.validYoutubeKey("AbCdEf_123-xy"))
    }

    @Test
    fun `artwork remains visible until the trailer has actually started`() {
        assertFalse(TrailerPreviewPolicy.shouldRevealPlayer(started = false, unavailable = false))
        assertTrue(TrailerPreviewPolicy.shouldRevealPlayer(started = true, unavailable = false))
        assertFalse(TrailerPreviewPolicy.shouldRevealPlayer(started = true, unavailable = true))
    }

    @Test
    fun `trailer exposes its own controls after playback begins`() {
        val html = youtubeEmbedHtml("AbCdEf_123-xy")

        assertTrue(html.contains("controls: 1"))
        assertFalse(html.contains("disablekb: 1"))
    }

    @Test
    fun `selected channel video uses audible full player controls`() {
        val html = youtubeEmbedHtml(
            youtubeKey = "AbCdEf_123-xy",
            muted = false,
            fullscreen = true,
            origin = "https://com.orgista.openstream",
        )

        assertTrue(html.contains("event.target.unMute()"))
        assertTrue(html.contains("event.target.setVolume(100)"))
        assertTrue(html.contains("controls: 1"))
        assertTrue(html.contains("fs: 1"))
        assertTrue(html.contains("origin: 'https://com.orgista.openstream'"))
    }

    @Test
    fun `tablet details overlay leaves the stage once a trailer is visible`() {
        assertTrue(TrailerPreviewPolicy.shouldShowDetailsOverlay(expandedLayout = true, trailerVisible = false))
        assertFalse(TrailerPreviewPolicy.shouldShowDetailsOverlay(expandedLayout = true, trailerVisible = true))
        assertFalse(TrailerPreviewPolicy.shouldShowDetailsOverlay(expandedLayout = false, trailerVisible = false))
    }
}
