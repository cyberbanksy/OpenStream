package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePreparedGuidePolicyTest {
    private fun channel(id: String, name: String) = IptvChannel(
        id = id,
        sourceId = "provider",
        sourceName = "Provider",
        name = name,
        group = "Entertainment",
        logoUrl = "",
        streamUrl = "https://example.test/$id.m3u8",
    )

    @Test
    fun `preparation builds route merge lineup guide map and both search indexes once`() {
        val cnn = channel("cnn", "CNN")
        val duplicate = cnn.copy(sourceId = "backup", streamUrl = "https://backup.test/cnn.m3u8")
        val program = IptvGuideProgram(
            channelKey = "provider:cnn",
            title = "Newsroom",
            startMillis = 1_000L,
            endMillis = 2_000L,
        )

        val prepared = LivePreparedGuidePolicy.build(
            channels = listOf(cnn, duplicate),
            programs = listOf(program),
            enabledPackageIds = setOf(LiveChannelPackage.ENTERTAINMENT.id),
            favoriteChannelKeys = emptySet(),
        )

        assertEquals(1, prepared.allChannels.size)
        assertEquals(listOf("cnn"), prepared.activeChannels.map(IptvChannel::id))
        assertEquals(listOf(program), prepared.guideByChannel["provider:cnn"])
        assertEquals(1, prepared.guideSearchIndex.size)
        assertEquals(1, prepared.fullCatalogSearchIndex.size)
        assertTrue(prepared.allChannels.single().fallbackStreamUrls.contains(duplicate.streamUrl))
    }
}
