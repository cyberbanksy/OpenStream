package com.orgista.openstream.iptv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyEmbeddedSourceCachePolicyTest {
    @Test
    fun `only former compiled source caches are removed during upgrade`() {
        assertTrue(LegacyEmbeddedSourceCachePolicy.shouldDelete("iptv-free-tv-us-v1.json"))
        assertTrue(LegacyEmbeddedSourceCachePolicy.shouldDelete("iptv-iptv-org-us-v1.json"))
        assertTrue(LegacyEmbeddedSourceCachePolicy.shouldDelete("iptv-guide-epgtalk-us-guide-v2.json"))
        assertTrue(LegacyEmbeddedSourceCachePolicy.shouldDelete("iptv-guide-free-tv-us-v2.attempt"))
        assertTrue(LegacyEmbeddedSourceCachePolicy.shouldDelete("iptv-guide-iptv-org-directv-guide-v1.attempt"))

        assertFalse(LegacyEmbeddedSourceCachePolicy.shouldDelete("iptv-guide-custom-v2.json"))
        assertFalse(LegacyEmbeddedSourceCachePolicy.shouldDelete("library-cache.json"))
        assertFalse(LegacyEmbeddedSourceCachePolicy.shouldDelete("stremio-catalog-v1.json"))
    }
}
