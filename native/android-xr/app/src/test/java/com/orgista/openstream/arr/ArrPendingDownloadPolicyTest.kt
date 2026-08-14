package com.orgista.openstream.arr

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrPendingDownloadPolicyTest {
    private val target = ArrDownloadTarget(
        title = "Example Show",
        year = 2025,
        tmdbId = 42,
        imdbId = "tt0000042",
        seasonNumber = 2,
    )

    @Test
    fun `pending requests round trip without connection credentials`() {
        val pending = PendingArrDownload(ArrKind.SONARR, target, queuedAtEpochMillis = 1234L)
        val encoded = ArrPendingDownloadCodec.encode(listOf(pending))

        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertEquals(listOf(pending), ArrPendingDownloadCodec.decode(encoded))
    }

    @Test
    fun `duplicate taps collapse to one durable request`() {
        val first = PendingArrDownload(ArrKind.SONARR, target, queuedAtEpochMillis = 1L)
        val replacement = first.copy(queuedAtEpochMillis = 2L)

        assertEquals(listOf(replacement), ArrPendingDownloadPolicy.upsert(listOf(first), replacement))
    }

    @Test
    fun `network and server failures queue but invalid requests do not`() {
        assertTrue(ArrPendingDownloadPolicy.shouldQueue(IOException("Connection refused")))
        assertTrue(ArrPendingDownloadPolicy.shouldQueue(IOException("Request returned HTTP 503.")))
        assertFalse(ArrPendingDownloadPolicy.shouldQueue(IOException("Request returned HTTP 401.")))
        assertFalse(ArrPendingDownloadPolicy.shouldQueue(IllegalArgumentException("Bad title")))
    }
}
