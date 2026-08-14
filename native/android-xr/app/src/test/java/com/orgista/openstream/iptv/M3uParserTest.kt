package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun `parses channel metadata groups logos and relative urls`() {
        val playlist = M3uParser.parsePlaylist(
            sourceId = "free-tv",
            playlistUrl = "https://media.example/lists/main.m3u8",
            content = """
                #EXTM3U x-tvg-url="https://guide.example/us.xml.gz, ../guide/local.xml"
                #EXTINF:-1 tvg-id="news.one" tvg-chno="7.1" tvg-name="News One" tvg-logo="https://img.example/news.png" http-user-agent="NewsPlayer/1.0" group-title="News",News One HD
                #EXTVLCOPT:http-referrer=https://media.example/guide
                ../streams/news.m3u8
                #EXTINF:-1 group-title="Movies",Cinema
                https://media.example/cinema.m3u8
            """.trimIndent(),
        )
        val channels = playlist.channels

        assertEquals(2, channels.size)
        assertEquals("News One", channels[0].name)
        assertEquals("news.one", channels[0].guideId)
        assertEquals("7.1", channels[0].channelNumber)
        assertEquals("News", channels[0].group)
        assertEquals("https://img.example/news.png", channels[0].logoUrl)
        assertEquals("https://media.example/streams/news.m3u8", channels[0].streamUrl)
        assertEquals("NewsPlayer/1.0", channels[0].httpUserAgent)
        assertEquals("https://media.example/guide", channels[0].httpReferer)
        assertEquals("Movies", channels[1].group)
        assertEquals(
            listOf("https://guide.example/us.xml.gz", "https://media.example/guide/local.xml"),
            playlist.guideUrls,
        )
    }

    @Test
    fun `ignores unsafe and incomplete entries`() {
        val channels = M3uParser.parse(
            sourceId = "test",
            playlistUrl = "https://media.example/list.m3u",
            content = """
                #EXTM3U
                #EXTINF:-1 group-title="Bad",Script
                javascript:alert(1)
                #EXTINF:-1,Valid
                https://media.example/valid.m3u8
                #EXTINF:-1,Missing URL
            """.trimIndent(),
        )

        assertEquals(listOf("Valid"), channels.map { it.name })
        assertTrue(channels.single().id.isNotBlank())
    }
}
