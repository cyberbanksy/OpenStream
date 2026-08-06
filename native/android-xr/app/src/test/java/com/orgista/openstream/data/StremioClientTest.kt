package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioClientTest {
    private val manifest = """
        {
          "id":"test.catalog",
          "name":"Test Catalog",
          "version":"1.2.3",
          "resources":["catalog", {"name":"meta", "types":["movie"]}, "stream"],
          "catalogs":[
            {"type":"movie","id":"popular","name":"Popular"},
            {"type":"series","id":"search","name":"Search","extra":[{"name":"search","isRequired":true}]}
          ]
        }
    """.trimIndent()

    @Test
    fun `manifest capabilities and usable catalogs are parsed`() {
        val addon = StremioClient.parseManifest("https://addon.example/config/manifest.json", manifest)

        assertEquals("test.catalog", addon.manifestId)
        assertEquals(setOf("catalog", "meta", "stream"), addon.resources)
        assertEquals(listOf("popular", "search"), addon.catalogs.map { it.id })
        assertFalse(addon.catalogs.first().requiresInput)
        assertTrue(addon.catalogs.last().requiresInput)
    }

    @Test
    fun `configured addon address is reduced to a safe host label`() {
        val label = StremioUrlPolicy.safeLabel("https://addon.example/private-token/config/manifest.json")

        assertEquals("addon.example · private configuration", label)
        assertFalse(label.contains("private-token"))
    }

    @Test
    fun `catalog metas map to browsable media and malformed entries are skipped`() {
        val addon = StremioClient.parseManifest("https://addon.example/manifest.json", manifest)
        val payload = """
            {"metas":[
              {"id":"tt1234567","type":"movie","name":"A Film","description":"Plot","releaseInfo":"2025","poster":"https://img.example/p.jpg","background":"https://img.example/b.jpg","genres":["Drama"],"director":"[]"},
              {"id":"missing-name","type":"movie"}
            ]}
        """.trimIndent()

        val items = StremioClient.parseCatalog(addon, payload)

        assertEquals(1, items.size)
        assertEquals("A Film", items.single().title)
        assertEquals("tt1234567", items.single().imdbId)
        assertEquals(listOf("Drama"), items.single().genres)
        assertEquals(null, items.single().director)
    }

    @Test
    fun `retry policy covers transient server failures only`() {
        assertTrue(StremioRetryPolicy.shouldRetry(httpCode = 500, attempt = 0))
        assertTrue(StremioRetryPolicy.shouldRetry(httpCode = 429, attempt = 1))
        assertFalse(StremioRetryPolicy.shouldRetry(httpCode = 404, attempt = 0))
        assertFalse(StremioRetryPolicy.shouldRetry(httpCode = 500, attempt = 3))
        assertEquals(250L, StremioRetryPolicy.delayMs(0))
        assertEquals(1_000L, StremioRetryPolicy.delayMs(2))
    }

    @Test
    fun `merging also removes malformed cached person placeholders`() {
        val malformed = com.orgista.openstream.data.MediaItem(
            id = "tt1",
            kind = MediaKind.MOVIE,
            title = "Film",
            synopsis = "",
            year = 2026,
            rating = "",
            runtimeMinutes = 0,
            director = "[]",
        )

        assertEquals(null, StremioCatalogMerger.mergeItem(malformed, malformed.copy(director = null)).director)
    }
}
