package com.orgista.openstream.iptv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvSessionRefreshPolicyTest {
    private val channel = IptvChannel(
        id = "cnn",
        sourceId = "provider",
        sourceName = "Provider",
        name = "CNN",
        group = "News",
        logoUrl = "",
        streamUrl = "https://example.com/cnn.m3u8",
    )

    @Test
    fun `back navigation reuses a recent lineup without refreshing`() {
        val now = 1_000_000L
        val snapshot = IptvSessionSnapshot(
            channels = listOf(channel),
            programs = emptyList(),
            revision = 7,
            refreshedAtMillis = now - 60_000L,
        )

        assertTrue(IptvSessionRefreshPolicy.canReuse(snapshot, revision = 7, nowMillis = now))
    }

    @Test
    fun `changed settings stale time and empty sessions refresh`() {
        val now = 10_000_000L
        val recent = IptvSessionSnapshot(listOf(channel), emptyList(), 2, now - 1_000L)
        val stale = recent.copy(refreshedAtMillis = now - IptvSessionRefreshPolicy.MAX_AGE_MILLIS - 1L)

        assertFalse(IptvSessionRefreshPolicy.canReuse(recent, revision = 3, nowMillis = now))
        assertFalse(IptvSessionRefreshPolicy.canReuse(stale, revision = 2, nowMillis = now))
        assertFalse(IptvSessionRefreshPolicy.canReuse(IptvSessionSnapshot(), revision = 2, nowMillis = now))
    }

    @Test
    fun `prewarmed cached lineup paints immediately but still refreshes providers`() {
        val snapshot = IptvSessionSnapshot(
            channels = listOf(channel),
            revision = 4,
            refreshedAtMillis = 0L,
        )

        assertFalse(IptvSessionRefreshPolicy.canReuse(snapshot, revision = 4, nowMillis = 10_000L))
    }

}
