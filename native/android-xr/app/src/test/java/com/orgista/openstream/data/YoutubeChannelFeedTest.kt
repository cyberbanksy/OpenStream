package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YoutubeChannelFeedTest {
    private val channelId = "UCfPyVJEBD7Di1YYjTdS2v8g"

    @Test
    fun `parses only validated recent videos from the approved channel`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:yt="http://www.youtube.com/xml/schemas/2015"
              xmlns:media="http://search.yahoo.com/mrss/">
              <yt:channelId>${channelId.removePrefix("UC")}</yt:channelId>
              <title>Homeschool Pop</title>
              <entry>
                <yt:videoId>Rf71QRZtVME</yt:videoId><yt:channelId>$channelId</yt:channelId>
                <title>School Supplies in Spanish for Kids</title>
                <published>2026-07-21T14:01:06+00:00</published>
                <media:group><media:thumbnail url="https://i3.ytimg.com/vi/Rf71QRZtVME/hqdefault.jpg"/></media:group>
              </entry>
              <entry>
                <yt:videoId>PI9tlYxluX0</yt:videoId><yt:channelId>UCBBBBBBBBBBBBBBBBBBBBBB</yt:channelId>
                <title>Foreign video</title>
              </entry>
            </feed>
        """.trimIndent()

        val feed = YoutubeChannelFeed.parse(xml, channelId)

        assertEquals("Homeschool Pop", feed.channelTitle)
        assertEquals(1, feed.videos.size)
        assertEquals("Rf71QRZtVME", feed.videos.single().videoId)
        assertEquals("https://i3.ytimg.com/vi/Rf71QRZtVME/hqdefault.jpg", feed.videos.single().thumbnailUrl)
    }

    @Test
    fun `replaces foreign artwork with a fixed youtube thumbnail`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:yt="http://www.youtube.com/xml/schemas/2015"
              xmlns:media="http://search.yahoo.com/mrss/">
              <yt:channelId>${channelId.removePrefix("UC")}</yt:channelId><title>Homeschool Pop</title>
              <entry><yt:videoId>PI9tlYxluX0</yt:videoId><yt:channelId>$channelId</yt:channelId>
                <title>Cactus Facts</title><media:thumbnail url="https://tracker.example/untrusted.jpg"/>
              </entry>
            </feed>
        """.trimIndent()

        assertEquals(
            "https://i.ytimg.com/vi/PI9tlYxluX0/hqdefault.jpg",
            YoutubeChannelFeed.parse(xml, channelId).videos.single().thumbnailUrl,
        )
    }

    @Test
    fun `rejects invalid ids foreign feeds and entity declarations`() {
        assertThrows(IllegalArgumentException::class.java) { YoutubeChannelFeed.feedUrl("@homeschoolpop") }
        assertThrows(IllegalArgumentException::class.java) {
            YoutubeChannelFeed.parse(
                """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015"><yt:channelId>wrong</yt:channelId></feed>""",
                channelId,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            YoutubeChannelFeed.parse(
                """<!DOCTYPE feed [<!ENTITY x SYSTEM "file:///etc/passwd">]><feed><title>&x;</title></feed>""",
                channelId,
            )
        }
    }
}
