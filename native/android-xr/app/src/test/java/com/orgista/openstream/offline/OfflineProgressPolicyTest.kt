package com.orgista.openstream.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineProgressPolicyTest {
    @Test
    fun `repairs signed 32 bit content length overflow for large downloads`() {
        assertEquals(
            4_199_075_805L,
            OfflineProgressPolicy.contentLength(
                reportedLength = -95_891_491L,
                bytesDownloaded = 4_199_075_805L,
                completed = true,
            ),
        )
    }

    @Test
    fun `completed download always presents one hundred percent`() {
        assertEquals(
            100,
            OfflineProgressPolicy.percent(
                reportedPercent = -1f,
                bytesDownloaded = 4_199_075_805L,
                contentLength = 4_199_075_805L,
                completed = true,
            ),
        )
    }

    @Test
    fun `derives active progress from byte counts when provider percent is unavailable`() {
        assertEquals(
            25,
            OfflineProgressPolicy.percent(
                reportedPercent = -1f,
                bytesDownloaded = 1_000_000_000L,
                contentLength = 4_000_000_000L,
                completed = false,
            ),
        )
    }

    @Test
    fun `unknown total does not pretend download is at zero percent`() {
        assertNull(
            OfflineProgressPolicy.percent(
                reportedPercent = -1f,
                bytesDownloaded = 500_000_000L,
                contentLength = -1L,
                completed = false,
            ),
        )
        assertEquals("Downloading · 477 MB saved", OfflineProgressPolicy.progressLabel(null, 500_000_000L))
    }
}
