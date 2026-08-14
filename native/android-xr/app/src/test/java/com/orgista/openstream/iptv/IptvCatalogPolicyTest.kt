package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvCatalogPolicyTest {
    private fun channel(id: String) = IptvChannel(
        id = id,
        sourceId = "free-tv-us",
        sourceName = "Free-TV USA",
        name = id,
        group = "USA",
        logoUrl = "",
        streamUrl = "https://media.example/$id.m3u8",
    )

    @Test
    fun `built in source uses the official small USA playlist`() {
        assertEquals(
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_usa.m3u8",
            IptvStore.FREE_TV.endpoint,
        )
        assertEquals(
            "https://iptv-org.github.io/iptv/countries/us.m3u",
            IptvStore.IPTV_ORG_US.endpoint,
        )
    }

    @Test
    fun `cached channels remain visible until a successful refresh replaces them`() {
        val cached = listOf(channel("cached"))
        val fresh = listOf(channel("fresh"))

        assertEquals(cached, IptvCatalogPolicy.initialChannels(cached))
        assertEquals(fresh, IptvCatalogPolicy.afterRefresh(cached, fresh))
        assertEquals(cached, IptvCatalogPolicy.afterRefresh(cached, emptyList()))
    }

    @Test
    fun `matching public channels become one card with an automatic fallback route`() {
        val primary = channel("cbs").copy(name = "CBS News")
        val fallback = channel("cbs-247").copy(
            sourceId = "iptv-org-us",
            sourceName = "IPTV-org USA",
            name = "CBS News 24/7 (720p)",
            streamUrl = "https://backup.example/cbs.m3u8",
        )

        val merged = IptvCatalogPolicy.mergeRoutes(listOf(primary, fallback))

        assertEquals(1, merged.size)
        assertEquals(primary.streamUrl, merged.single().streamUrl)
        assertEquals(listOf(fallback.streamUrl), merged.single().fallbackStreamUrls)
    }

    @Test
    fun `playback route priority prefers configured service then current public catalog`() {
        val backup = channel("abc-backup").copy(
            sourceId = "free-tv-us",
            name = "ABC News",
            streamUrl = "https://backup.example/abc.m3u8",
        )
        val currentPublic = channel("abc-current").copy(
            sourceId = "iptv-org-us",
            name = "ABC News (720p)",
            streamUrl = "https://current.example/abc.m3u8",
        )
        val configured = channel("abc-provider").copy(
            sourceId = "custom",
            sourceName = "My TV",
            name = "ABC News HD",
            streamUrl = "https://provider.example/abc.m3u8",
        )

        val merged = IptvCatalogPolicy.mergeForPlayback(listOf(backup, currentPublic, configured))

        assertEquals(configured.streamUrl, merged.single().streamUrl)
        assertEquals(
            listOf(currentPublic.streamUrl, backup.streamUrl),
            merged.single().fallbackStreamUrls,
        )
    }

    @Test
    fun `short station call signs merge so configured CNN replaces a broken public route`() {
        val public = channel("cnn-public").copy(
            sourceId = "free-tv-us",
            name = "CNN",
            streamUrl = "https://public.example/cnn.m3u8",
        )
        val configured = channel("cnn-provider").copy(
            sourceId = "custom",
            sourceName = "My TV",
            name = "CNN SD",
            streamUrl = "https://provider.example/cnn.m3u8",
        )

        val merged = IptvCatalogPolicy.mergeForPlayback(listOf(public, configured))

        assertEquals(1, merged.size)
        assertEquals(configured.streamUrl, merged.single().streamUrl)
        assertEquals(listOf(public.streamUrl), merged.single().fallbackStreamUrls)
    }

    @Test
    fun `quality and coast labels merge into alternate playback routes`() {
        val hd = channel("comedy-hd").copy(name = "Comedy Central HD")
        val fhdWest = channel("comedy-fhd").copy(
            sourceId = "custom",
            name = "Comedy Central FHD West",
            streamUrl = "https://provider.example/comedy.m3u8",
        )

        val merged = IptvCatalogPolicy.mergeForPlayback(listOf(hd, fhdWest))

        assertEquals(1, merged.size)
        assertEquals(fhdWest.streamUrl, merged.single().streamUrl)
    }

    @Test
    fun `two letter names remain separate because they are too ambiguous`() {
        val first = channel("ax-one").copy(name = "AX", streamUrl = "https://one.example/ax.m3u8")
        val second = channel("ax-two").copy(
            sourceId = "custom",
            name = "AX HD",
            streamUrl = "https://two.example/ax.m3u8",
        )

        assertEquals(2, IptvCatalogPolicy.mergeForPlayback(listOf(first, second)).size)
    }

    @Test
    fun `public catalog cache round trips without private account data`() {
        val original = listOf(channel("abc-news"))
        val encoded = IptvCatalogCodec.encode("free-tv-us", original)

        assertEquals(original, IptvCatalogCodec.decode("free-tv-us", encoded))
        assertEquals(emptyList<IptvChannel>(), IptvCatalogCodec.decode("another-source", encoded))
    }
}
