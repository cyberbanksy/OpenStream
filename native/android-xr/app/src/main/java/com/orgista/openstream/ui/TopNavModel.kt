package com.orgista.openstream.ui

/** Header destinations are split so the always-visible row stays calm on tablets and TVs. */
enum class TopDestination {
    DISCOVER,
    LIBRARY,
    LIVE,
    SOURCES,
    SETTINGS,
}

object TopNavLayoutPolicy {
    /** Android already renders network and battery status; duplicating it made the app bar noisy. */
    const val showConnectivityTelemetry = false
    val brandDestination = TopDestination.DISCOVER

    fun isCompact(widthDp: Int, heightDp: Int): Boolean = minOf(widthDp, heightDp) < 600

    /** The wide tab includes a 2dp selection rail; compensate its visual center beside the brand. */
    fun brandBottomPaddingDp(compact: Boolean): Int = if (compact) 0 else 7
}

fun primaryTopDestinations(): List<TopDestination> = listOf(
    TopDestination.DISCOVER,
    TopDestination.LIBRARY,
    TopDestination.LIVE,
)

fun overflowTopDestinations(kidsMode: Boolean): List<TopDestination> = buildList {
    if (!kidsMode) add(TopDestination.SOURCES)
    add(TopDestination.SETTINGS)
}

fun libraryHeaderStatus(
    indexing: Boolean,
    hasLibraries: Boolean,
    titleCount: Int,
    metadataEnabled: Boolean,
): String = when {
    indexing -> "Scanning library…"
    !hasLibraries -> "Add a source"
    !metadataEnabled -> "$titleCount titles · artwork needs TMDB"
    else -> "$titleCount titles"
}
