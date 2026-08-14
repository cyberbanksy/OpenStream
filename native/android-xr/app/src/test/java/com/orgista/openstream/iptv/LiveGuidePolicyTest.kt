package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveGuidePolicyTest {
    private val channel = IptvChannel(
        id = "pbs",
        sourceId = "provider",
        sourceName = "Provider",
        name = "PBS Metro",
        group = "Local",
        logoUrl = "",
        streamUrl = "https://media.example/pbs.m3u8",
    )
    private val start = Instant.parse("2026-08-13T01:00:00Z").toEpochMilli()

    @Test
    fun `guide window snaps to half hour and can move through time`() {
        val now = Instant.parse("2026-08-13T01:18:27Z").toEpochMilli()

        assertEquals(start, LiveGuidePolicy.windowStart(now, 0))
        assertEquals(start + 30 * 60_000L, LiveGuidePolicy.windowStart(now, 1))
        assertEquals(start - 60 * 60_000L, LiveGuidePolicy.windowStart(now, -2))
    }

    @Test
    fun `program segment clips to visible timeline`() {
        val program = IptvGuideProgram(
            channelKey = LiveChannelPackagePolicy.channelKey(channel),
            title = "Nature Now",
            startMillis = start - 15 * 60_000L,
            endMillis = start + 45 * 60_000L,
        )

        val segment = LiveGuidePolicy.segment(program, start, start + 120 * 60_000L)

        assertEquals(0f, segment.startFraction, 0.001f)
        assertEquals(0.375f, segment.widthFraction, 0.001f)
    }

    @Test
    fun `search includes current and upcoming program titles`() {
        val key = LiveChannelPackagePolicy.channelKey(channel)
        val programs = listOf(
            IptvGuideProgram(key, "Nature Now", startMillis = start, endMillis = start + 60 * 60_000L),
            IptvGuideProgram(key, "PBS NewsHour", startMillis = start + 60 * 60_000L, endMillis = start + 120 * 60_000L),
        )

        assertTrue(LiveGuidePolicy.matchesSearch(channel, programs, "newshour"))
        assertFalse(LiveGuidePolicy.matchesSearch(channel, programs, "basketball"))
    }

    @Test
    fun `fallback guide fills only channels missing provider schedules`() {
        val other = channel.copy(id = "cnn", name = "CNN")
        val primary = listOf(
            IptvGuideProgram(
                LiveChannelPackagePolicy.channelKey(channel),
                "Provider Schedule",
                startMillis = start,
                endMillis = start + 60 * 60_000L,
            ),
        )
        val fallback = listOf(
            IptvGuideProgram(
                LiveChannelPackagePolicy.channelKey(channel),
                "Duplicate Fallback",
                startMillis = start,
                endMillis = start + 60 * 60_000L,
            ),
            IptvGuideProgram(
                LiveChannelPackagePolicy.channelKey(other),
                "The Lead",
                startMillis = start,
                endMillis = start + 60 * 60_000L,
            ),
        )

        val merged = LiveGuidePolicy.fillMissingSchedules(primary, fallback)

        assertEquals(listOf("Provider Schedule", "The Lead"), merged.map { it.title })
    }

    @Test
    fun `guide matcher recognizes affiliate call signs in different name orders`() {
        val local = channel.copy(name = "ABC (WFAA) Dallas, TX")

        val key = IptvGuideMatchPolicy.channelKey(
            guideChannelId = "WFAA",
            displayNames = listOf("Dallas, TX WFAA ABC 8 A3 HD"),
            channels = listOf(local),
        )

        assertEquals(LiveChannelPackagePolicy.channelKey(local), key)
    }

    @Test
    fun `guide matcher recognizes local network and city when source omits call sign`() {
        val local = channel.copy(name = "ABC (WFAA) Dallas, TX")

        val key = IptvGuideMatchPolicy.channelKey(
            guideChannelId = "I8.1.gracenote.com",
            displayNames = listOf("ABC Dallas", "8.1 ABC Dallas"),
            channels = listOf(local),
        )

        assertEquals(LiveChannelPackagePolicy.channelKey(local), key)
    }

    @Test
    fun `guide matcher treats Dallas and Fort Worth as the same local market`() {
        val local = channel.copy(name = "NBC (KXAS) Fort Worth, TX")

        val key = IptvGuideMatchPolicy.channelKey(
            guideChannelId = "I5.1.19627.gracenote.com",
            displayNames = listOf("US - NBC News Dallas Fortworth"),
            channels = listOf(local),
        )

        assertEquals(LiveChannelPackagePolicy.channelKey(local), key)
    }

    @Test
    fun `guide matcher does not confuse Fort Myers with Fort Worth`() {
        val local = channel.copy(name = "NBC (KXAS) Fort Worth, TX")

        val key = IptvGuideMatchPolicy.channelKey(
            guideChannelId = "I20.1.32343.gracenote.com",
            displayNames = listOf("NBC Fort Myers"),
            channels = listOf(local),
        )

        assertNull(key)
    }

    @Test
    fun `guide matcher does not assign a city affiliate to a generic news stream`() {
        val news = channel.copy(name = "NBC News")

        val key = IptvGuideMatchPolicy.channelKey(
            guideChannelId = "I2.1.gracenote.com",
            displayNames = listOf("NBC News Houston"),
            channels = listOf(news),
        )

        assertNull(key)
    }
}
