package com.orgista.openstream.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHlsPlaybackPolicyTest {
    @Test
    fun `hls route uses the malformed caption descriptor guard`() {
        assertTrue(LiveHlsPlaybackPolicy.useCaptionDescriptorGuard("https://example.test/live/abc.m3u8"))
        assertTrue(LiveHlsPlaybackPolicy.useCaptionDescriptorGuard("https://backup.test/abc.m3u8?token=secret"))
    }

    @Test
    fun `progressive live route retains automatic extraction`() {
        assertFalse(LiveHlsPlaybackPolicy.useCaptionDescriptorGuard("https://example.test/live/abc.ts"))
        assertFalse(LiveHlsPlaybackPolicy.useCaptionDescriptorGuard(""))
    }
}
