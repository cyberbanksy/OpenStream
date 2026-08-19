package com.orgista.openstream.ui

import kotlin.math.min

enum class CinematicArtworkFit {
    FULL_BLEED,
    FIT_WITH_AMBIENT_BANDS,
}

/** Geometry shared by Featured and Details cinematic artwork stages. */
object ArtworkPresentationPolicy {
    /**
     * Featured and Details are edge-to-edge media stages. Fitting a portrait-ish
     * backdrop leaves a hard rectangular edge inside wide tablet displays, so
     * these surfaces use a center crop while title/actions remain in safe areas.
     */
    val cinematicArtworkFit = CinematicArtworkFit.FULL_BLEED

    fun stageHeightDp(widthDp: Float, screenHeightDp: Float): Float =
        min(widthDp * 9f / 16f, screenHeightDp).coerceAtLeast(0f)

    fun usesExpandedDetailsOverlay(widthDp: Float, heightDp: Float): Boolean =
        widthDp >= 720f && widthDp > heightDp

    fun detailsOverlayWidthDp(widthDp: Float): Float =
        (widthDp * 0.56f).coerceIn(520f, 760f)
}
