package com.orgista.openstream.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkPresentationPolicyTest {
    @Test
    fun `landscape artwork stage uses the full 16 by 9 width without the old height cap`() {
        assertEquals(
            720f,
            ArtworkPresentationPolicy.stageHeightDp(widthDp = 1280f, screenHeightDp = 800f),
            0.01f,
        )
    }

    @Test
    fun `artwork stage never exceeds the available display height`() {
        assertEquals(
            600f,
            ArtworkPresentationPolicy.stageHeightDp(widthDp = 1600f, screenHeightDp = 600f),
            0.01f,
        )
    }

    @Test
    fun `cinematic foreground fills the stage instead of exposing letterbox bands`() {
        assertEquals(
            CinematicArtworkFit.FULL_BLEED,
            ArtworkPresentationPolicy.cinematicArtworkFit,
        )
    }

    @Test
    fun `landscape tablet details place information over the artwork`() {
        assertEquals(
            true,
            ArtworkPresentationPolicy.usesExpandedDetailsOverlay(widthDp = 1280f, heightDp = 800f),
        )
    }

    @Test
    fun `portrait phone details keep information below the compact artwork`() {
        assertEquals(
            false,
            ArtworkPresentationPolicy.usesExpandedDetailsOverlay(widthDp = 390f, heightDp = 844f),
        )
    }

    @Test
    fun `details overlay stays readable instead of spanning the full tablet`() {
        assertEquals(716.8f, ArtworkPresentationPolicy.detailsOverlayWidthDp(1280f), 0.01f)
        assertEquals(520f, ArtworkPresentationPolicy.detailsOverlayWidthDp(700f), 0.01f)
    }
}
