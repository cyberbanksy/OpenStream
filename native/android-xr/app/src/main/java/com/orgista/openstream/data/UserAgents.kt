package com.orgista.openstream.data

import com.orgista.openstream.BuildConfig

/**
 * Single source of truth for the HTTP User-Agent strings the app sends. The
 * product token is derived from the build's versionName so a version bump
 * updates every request automatically, instead of drifting across the copies
 * that used to be hand-typed in each network client.
 */
object UserAgents {
    /** e.g. "OpenStream/0.1.0" — base product token for first-party requests. */
    val product: String = "OpenStream/${BuildConfig.VERSION_NAME}"

    /** Sonarr/Radarr (ARR) system calls. */
    val arr: String = "$product ARR"

    /** Cloudflare speed-test probe. */
    val speedTest: String = "$product speed-test"

    /** Live/IPTV playback HTTP data source. */
    val live: String = "$product Live"

    /**
     * Spoofed mobile-browser UA for endpoints that reject app-style clients
     * (YouTube watch/feed pages, and Cloudflare's browser-origin retry).
     */
    const val browser: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
}
