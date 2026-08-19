package com.orgista.openstream.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRouteRetryPolicyTest {
    @Test
    fun `preferred route retries transient provider failures`() {
        assertTrue(PlaybackRouteRetryPolicy.shouldRetry(502, attempt = 0, preferredRoute = true))
        assertTrue(PlaybackRouteRetryPolicy.shouldRetry(429, attempt = 1, preferredRoute = true))
        assertTrue(PlaybackRouteRetryPolicy.shouldRetry(408, attempt = 0, preferredRoute = true))
    }

    @Test
    fun `permanent responses and alternate routes fail over immediately`() {
        assertFalse(PlaybackRouteRetryPolicy.shouldRetry(404, attempt = 0, preferredRoute = true))
        assertFalse(PlaybackRouteRetryPolicy.shouldRetry(502, attempt = 0, preferredRoute = false))
        assertFalse(PlaybackRouteRetryPolicy.shouldRetry(null, attempt = 0, preferredRoute = true))
    }

    @Test
    fun `live channel startup failures fail over immediately`() {
        assertFalse(
            PlaybackRouteRetryPolicy.shouldRetry(
                statusCode = 503,
                attempt = 0,
                preferredRoute = true,
                isLive = true,
                playedSuccessfully = false,
            ),
        )
    }

    @Test
    fun `established live channel reconnects transient failures`() {
        assertTrue(
            PlaybackRouteRetryPolicy.shouldRetry(
                statusCode = null,
                attempt = 0,
                preferredRoute = true,
                isLive = true,
                playedSuccessfully = true,
            ),
        )
        assertTrue(
            PlaybackRouteRetryPolicy.shouldRetry(
                statusCode = 503,
                attempt = 1,
                preferredRoute = true,
                isLive = true,
                playedSuccessfully = true,
            ),
        )
        assertFalse(
            PlaybackRouteRetryPolicy.shouldRetry(
                statusCode = 403,
                attempt = 0,
                preferredRoute = true,
                isLive = true,
                playedSuccessfully = true,
            ),
        )
    }

    @Test
    fun `retry budget is bounded`() {
        assertTrue(PlaybackRouteRetryPolicy.shouldRetry(503, attempt = 1, preferredRoute = true))
        assertFalse(PlaybackRouteRetryPolicy.shouldRetry(503, attempt = 2, preferredRoute = true))
        assertEquals(1_500L, PlaybackRouteRetryPolicy.delayMs(attempt = 0))
        assertEquals(4_000L, PlaybackRouteRetryPolicy.delayMs(attempt = 1))
    }

    @Test
    fun `live HTTP failures use provider friendly copy`() {
        assertEquals(
            "The provider denied this channel (HTTP 403). It may require an active package or a refreshed provider link.",
            PlaybackRouteRetryPolicy.terminalMessage(403, isLive = true, errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS"),
        )
        assertEquals(
            "This channel is no longer available from the provider.",
            PlaybackRouteRetryPolicy.terminalMessage(404, isLive = true, errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS"),
        )
    }
}
