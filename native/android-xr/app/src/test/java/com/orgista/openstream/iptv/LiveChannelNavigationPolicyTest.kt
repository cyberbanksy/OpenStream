package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveChannelNavigationPolicyTest {
    private fun channel(id: String, number: String) = IptvChannel(
        id = id,
        sourceId = "provider",
        sourceName = "Provider",
        name = id.uppercase(),
        group = "Entertainment",
        logoUrl = "",
        streamUrl = "https://example.test/$id.m3u8",
        channelNumber = number,
    )

    private val channels = listOf(channel("one", "1"), channel("two", "2"), channel("three", "3"))

    @Test
    fun `channel up and down follow the visible lineup`() {
        assertEquals(
            "three",
            LiveChannelNavigationPolicy.adjacent(channels, "provider:two", direction = 1)?.id,
        )
        assertEquals(
            "one",
            LiveChannelNavigationPolicy.adjacent(channels, "provider:two", direction = -1)?.id,
        )
    }

    @Test
    fun `channel switching wraps at lineup ends`() {
        assertEquals(
            "one",
            LiveChannelNavigationPolicy.adjacent(channels, "provider:three", direction = 1)?.id,
        )
        assertEquals(
            "three",
            LiveChannelNavigationPolicy.adjacent(channels, "provider:one", direction = -1)?.id,
        )
    }

    @Test
    fun `missing and empty lineups cannot switch`() {
        assertNull(LiveChannelNavigationPolicy.adjacent(emptyList(), "provider:one", direction = 1))
        assertNull(LiveChannelNavigationPolicy.adjacent(channels, "provider:missing", direction = 1))
        assertNull(LiveChannelNavigationPolicy.adjacent(channels, "provider:one", direction = 0))
    }

    @Test
    fun `channel navigation survives a guide refresh that replaces the route key`() {
        val refreshed = listOf(
            channel("cnn-new", "1").copy(name = "CNN HD"),
            channel("abc", "4").copy(name = "ABC News"),
        )

        assertEquals(
            "abc",
            LiveChannelNavigationPolicy.adjacent(
                channels = refreshed,
                currentChannelKey = "stale:cnn-old",
                direction = 1,
                currentChannelName = "CNN",
            )?.id,
        )
    }
}
