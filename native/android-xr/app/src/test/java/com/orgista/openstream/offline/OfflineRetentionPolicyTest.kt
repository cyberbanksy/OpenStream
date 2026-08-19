package com.orgista.openstream.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRetentionPolicyTest {
    @Test
    fun `completed offline playback is deleted only when the viewer opts in`() {
        assertTrue(
            OfflineRetentionPolicy.shouldDelete(
                deleteAfterWatched = true,
                playbackEnded = true,
                offlineDownloadId = "offline-episode",
            ),
        )
        assertFalse(
            OfflineRetentionPolicy.shouldDelete(
                deleteAfterWatched = false,
                playbackEnded = true,
                offlineDownloadId = "offline-episode",
            ),
        )
    }

    @Test
    fun `online streams and unfinished downloads are retained`() {
        assertFalse(
            OfflineRetentionPolicy.shouldDelete(
                deleteAfterWatched = true,
                playbackEnded = true,
                offlineDownloadId = null,
            ),
        )
        assertFalse(
            OfflineRetentionPolicy.shouldDelete(
                deleteAfterWatched = true,
                playbackEnded = false,
                offlineDownloadId = "offline-movie",
            ),
        )
    }
}
