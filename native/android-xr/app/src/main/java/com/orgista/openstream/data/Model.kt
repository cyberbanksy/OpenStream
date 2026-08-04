package com.orgista.openstream.data

import androidx.annotation.DrawableRes

enum class MediaKind { MOVIE, SERIES, VIDEO }

enum class SourceKind { DEMO, NAS, STREMIO }

/** One playable route for a title. */
data class MediaCandidate(
    val id: String,
    val sourceName: String,
    val sourceKind: SourceKind,
    /** Player URI: rawresource:// for demo, smb:// for NAS, http(s) for add-ons. */
    val uri: String,
    val formatLabel: String,
    val directPlay: Boolean = true,
)

data class MediaItem(
    val id: String,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val synopsis: String,
    val year: Int,
    /** MPAA/TV certification; empty means unrated (blocked in Kids Mode). */
    val rating: String,
    val runtimeMinutes: Int,
    @DrawableRes val artRes: Int? = null,
    /** Remote poster (TMDB); takes precedence over artRes when present. */
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val imdbId: String? = null,
    val progressPercent: Int? = null,
    val candidates: List<MediaCandidate> = emptyList(),
    /** For series: the episodes grouped under this show, season-ordered. */
    val episodes: List<MediaItem> = emptyList(),
)
