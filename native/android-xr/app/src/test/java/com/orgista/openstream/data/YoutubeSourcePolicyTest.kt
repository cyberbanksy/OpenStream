package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSourcePolicyTest {
    private val channel = YoutubeChannelSource(
        id = "source-1",
        channelId = "UCfPyVJEBD7Di1YYjTdS2v8g",
        name = "Homeschool Pop",
        canonicalUrl = "https://www.youtube.com/channel/UCfPyVJEBD7Di1YYjTdS2v8g",
        thumbnailUrl = null,
    )

    @Test
    fun `builds a channel discover rail with sixteen by nine video art`() {
        val section = YoutubeSourcePolicy.section(
            channel,
            listOf(
                YoutubeChannelVideo(
                    videoId = "Rf71QRZtVME",
                    title = "School Supplies in Spanish",
                    thumbnailUrl = "https://i.ytimg.com/vi/Rf71QRZtVME/hqdefault.jpg",
                    publishedAt = "2026-07-21T14:01:06+00:00",
                ),
            ),
        )

        assertEquals("YouTube", section.addonName)
        assertEquals("Homeschool Pop", section.catalogName)
        assertEquals(MediaKind.VIDEO, section.items.single().kind)
        assertEquals(2026, section.items.single().year)
        assertEquals("Homeschool Pop", section.items.single().subtitle)
        assertEquals("Rf71QRZtVME", YoutubeSourcePolicy.videoId(section.items.single()))
        assertTrue(section.items.single().posterUrl!!.contains("Rf71QRZtVME"))
    }

    @Test
    fun `does not treat ordinary catalog items as youtube videos`() {
        val item = MediaItem(
            id = "tt1234567",
            kind = MediaKind.MOVIE,
            title = "Movie",
            synopsis = "",
            year = 2025,
            rating = "PG",
            runtimeMinutes = 100,
        )
        assertNull(YoutubeSourcePolicy.videoId(item))
    }

    @Test
    fun `source codec preserves enabled state without credentials`() {
        val encoded = YoutubeChannelSourceCodec.encode(listOf(channel.copy(enabled = false)))
        val decoded = YoutubeChannelSourceCodec.decode(encoded)

        assertEquals(listOf(channel.copy(enabled = false)), decoded)
        assertTrue("API keys must never be present", !encoded.contains("apiKey", ignoreCase = true))
    }
}
