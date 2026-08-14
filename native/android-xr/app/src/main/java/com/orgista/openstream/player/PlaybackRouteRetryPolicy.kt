package com.orgista.openstream.player

/** Keeps short-lived provider outages from immediately discarding the best compatible route. */
object PlaybackRouteRetryPolicy {
    const val MAX_RETRIES = 2

    fun shouldRetry(
        statusCode: Int?,
        attempt: Int,
        preferredRoute: Boolean,
        isLive: Boolean = false,
        playedSuccessfully: Boolean = false,
    ): Boolean {
        if (attempt !in 0 until MAX_RETRIES) return false
        val transientHttpFailure = statusCode == 408 || statusCode == 429 || statusCode in 500..599
        return if (isLive) {
            playedSuccessfully && (statusCode == null || transientHttpFailure)
        } else {
            preferredRoute && statusCode != null && transientHttpFailure
        }
    }

    fun delayMs(attempt: Int): Long = when (attempt) {
        0 -> 1_500L
        else -> 4_000L
    }

    fun terminalMessage(statusCode: Int?, isLive: Boolean, errorCodeName: String): String = when {
        isLive && statusCode == 404 -> "This channel is no longer available from the provider."
        isLive && statusCode == 401 -> "The provider rejected the Live TV sign-in. Check the account in Settings."
        isLive && statusCode == 403 ->
            "The provider denied this channel (HTTP 403). It may require an active package or a refreshed provider link."
        isLive && statusCode == 429 -> "This channel is busy. Please try again shortly."
        isLive -> "This channel could not be started."
        else -> errorCodeName
            .removePrefix("ERROR_CODE_")
            .replace('_', ' ')
            .lowercase()
    }
}
