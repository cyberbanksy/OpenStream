package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XtreamClientTest {
    @Test
    fun `connection diagnostics redact account credentials`() {
        val source = IptvSource.xtream(
            id = "custom",
            name = "Home TV",
            baseUrl = "https://tv.example.com",
            username = "viewer@example.com",
            password = "top-secret",
        )

        assertFalse(source.toString().contains("viewer@example.com"))
        assertFalse(source.toString().contains("top-secret"))
    }

    @Test
    fun `xtream channel response becomes playable channels without logging credentials`() {
        val source = IptvSource.xtream(
            id = "custom",
            name = "Home TV",
            baseUrl = "https://tv.example.com/panel",
            username = "viewer@example.com",
            password = "p@ss word",
        )
        val channels = XtreamClient.parseChannels(
            source,
            """[{"stream_id":42,"num":207,"epg_channel_id":"KDFW.us","name":"Local News","category_name":"News","stream_icon":"https://img.example/42.png","container_extension":"m3u8"}]""",
        )

        assertEquals(1, channels.size)
        assertEquals("Local News", channels.single().name)
        assertEquals("News", channels.single().group)
        assertEquals("KDFW.us", channels.single().guideId)
        assertEquals("207", channels.single().channelNumber)
        assertEquals(
            "https://tv.example.com/panel/live/viewer%40example.com/p%40ss%20word/42.m3u8",
            channels.single().streamUrl,
        )
    }

    @Test
    fun `empty scheduled event placeholders are excluded while named ppv events remain`() {
        val source = IptvSource.xtream(
            id = "custom",
            name = "Home TV",
            baseUrl = "https://tv.example.com",
            username = "viewer",
            password = "secret",
        )
        val channels = XtreamClient.parseChannels(
            source,
            """[
                {"stream_id":1,"name":"EVENTS 01: ","category_name":"Events"},
                {"stream_id":2,"name":"EVENTS 02: Championship Boxing","category_name":"Events"}
            ]""",
        )

        assertEquals(listOf("EVENTS 02: Championship Boxing"), channels.map { it.name })
    }
}
