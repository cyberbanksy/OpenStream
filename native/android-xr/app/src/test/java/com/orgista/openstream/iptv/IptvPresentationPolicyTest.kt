package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvPresentationPolicyTest {
    @Test
    fun `provider boilerplate and trailing feed codes are removed`() {
        assertEquals("Movies", IptvPresentationPolicy.category("IPTV-org USA Movies;X"))
        assertEquals("News", IptvPresentationPolicy.category("US | News HD"))
    }

    @Test
    fun `unknown useful provider category remains readable`() {
        assertEquals("Classic Comedy", IptvPresentationPolicy.category("USA; Classic Comedy"))
    }

    @Test
    fun `public provider names become friendly`() {
        assertEquals("Live TV", IptvPresentationPolicy.sourceName("IPTV-org USA"))
        assertEquals("Live TV", IptvPresentationPolicy.sourceName("Free-TV USA"))
        assertEquals("Home TV", IptvPresentationPolicy.sourceName("Home TV"))
    }
}
