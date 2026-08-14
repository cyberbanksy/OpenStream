package com.orgista.openstream.data

import androidx.annotation.DrawableRes

enum class MediaKind { MOVIE, SERIES, VIDEO }

enum class SourceKind { DEMO, NAS, STREMIO, OFFLINE }

/** One playable route for a title. */
data class MediaCandidate(
    val id: String,
    val sourceName: String,
    val sourceKind: SourceKind,
    /** Player URI: rawresource:// for demo, smb:// for NAS, http(s) for add-ons. */
    val uri: String,
    val formatLabel: String,
    val directPlay: Boolean = true,
    /** Media3 download id when this route must read only from the offline cache. */
    val offlineDownloadId: String? = null,
    /** False for legacy downloads whose provider filename was never matched to the requested title. */
    val offlineIdentityVerified: Boolean = true,
)

/** Aggregated critic/audience scores, Infuse-style. Any may be null. */
data class TitleRatings(
    val imdb: String? = null,
    val rottenTomatoes: String? = null,
    val metacritic: String? = null,
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
    @param:DrawableRes val artRes: Int? = null,
    /** Remote poster (TMDB); takes precedence over artRes when present. */
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /** Official YouTube trailer id selected from TMDB video metadata. */
    val trailerYoutubeKey: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val progressPercent: Int? = null,
    val candidates: List<MediaCandidate> = emptyList(),
    /** For series: the episodes grouped under this show, season-ordered. */
    val episodes: List<MediaItem> = emptyList(),
    // Rich metadata, filled lazily when a title's details screen opens.
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val tagline: String? = null,
    val ratings: TitleRatings? = null,
)
