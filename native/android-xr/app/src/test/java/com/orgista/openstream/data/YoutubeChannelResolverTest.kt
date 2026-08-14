package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class YoutubeChannelResolverTest {
    @Test
    fun `normalizes exact handles ids and channel urls`() {
        assertEquals(
            "https://www.youtube.com/@GoogleDevelopers",
            YoutubeChannelResolver.lookupUrl("Google Developers"),
        )
        assertEquals(
            "https://www.youtube.com/@GoogleDevelopers",
            YoutubeChannelResolver.lookupUrl("youtube.com/@GoogleDevelopers/videos"),
        )
        assertEquals(
            "https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw",
            YoutubeChannelResolver.lookupUrl("UC_x5XG1OV2P6uZZ5FSM9Ttw"),
        )
    }

    @Test
    fun `rejects lookalike hosts and non channel urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            YoutubeChannelResolver.lookupUrl("https://youtube.example/@GoogleDevelopers")
        }
        assertThrows(IllegalArgumentException::class.java) {
            YoutubeChannelResolver.lookupUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        }
    }

    @Test
    fun `extracts canonical id title and trusted artwork`() {
        val html = """
            <html><head>
              <meta property="og:title" content="Google &amp; Developers">
              <meta property="og:image" content="https://yt3.ggpht.com/channel-avatar=s900-c-k-c0x00ffffff-no-rj">
            </head><body>https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw</body></html>
        """.trimIndent()

        val channel = YoutubeChannelResolver.parseVerifiedPage(html)

        assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", channel.channelId)
        assertEquals("Google & Developers", channel.name)
        assertEquals(
            "https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw",
            channel.canonicalUrl,
        )
        assertEquals("https://yt3.ggpht.com/channel-avatar=s900-c-k-c0x00ffffff-no-rj", channel.thumbnailUrl)
    }

    @Test
    fun `rejects unverifiable pages and drops untrusted channel artwork`() {
        assertThrows(IllegalArgumentException::class.java) {
            YoutubeChannelResolver.parseVerifiedPage("<html>Channel unavailable</html>")
        }
        val channel = YoutubeChannelResolver.parseVerifiedPage(
            """
                <meta property="og:title" content="Example Channel">
                <meta property="og:image" content="https://tracker.example/avatar.jpg">
                https://www.youtube.com/channel/UCAAAAAAAAAAAAAAAAAAAAAA
            """.trimIndent(),
        )
        assertNull(channel.thumbnailUrl)
    }
}
