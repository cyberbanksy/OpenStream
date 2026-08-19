package com.orgista.openstream.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvGuideSourcePolicyTest {
    @Test
    fun `empty provider guide input does not invent a guide source`() {
        assertEquals(emptyList<String>(), IptvGuideSourcePolicy.select(emptyList(), "My TV"))
    }

    @Test
    fun `large USA guide lists prefer bounded national and sports feeds`() {
        val urls = listOf(
            "https://guide.example/epg_ripper_ALL_SOURCES1.xml.gz",
            "https://guide.example/epg_ripper_CA1.xml.gz",
            "https://guide.example/epg_ripper_US1.xml.gz",
            "https://guide.example/epg_ripper_US_LOCALS2.xml.gz",
            "https://guide.example/epg_ripper_US_SPORTS1.xml.gz",
        )

        assertEquals(
            listOf(
                "https://guide.example/epg_ripper_US1.xml.gz",
                "https://guide.example/epg_ripper_US_SPORTS1.xml.gz",
            ),
            IptvGuideSourcePolicy.select(urls, "Free-TV USA"),
        )
    }

    @Test
    fun `stale numbered epgshare URL gets one bounded fallback`() {
        val candidates = IptvGuideSourcePolicy.retryCandidates(
            "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz",
        )

        assertTrue("https://epgshare01.online/epgshare01/epg_ripper_US2.xml.gz" in candidates)
        assertEquals(2, candidates.size)
    }
}
