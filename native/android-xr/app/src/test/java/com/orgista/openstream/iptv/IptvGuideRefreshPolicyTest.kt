package com.orgista.openstream.iptv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvGuideRefreshPolicyTest {
    @Test
    fun `recent guide result skips expensive refresh`() {
        val now = 1_000_000_000L

        assertTrue(IptvGuideRefreshPolicy.isFresh(now - 60_000L, now, hasPrograms = true))
        assertTrue(IptvGuideRefreshPolicy.isFresh(now - 60_000L, now, hasPrograms = false))
        assertFalse(IptvGuideRefreshPolicy.isFresh(now - IptvGuideRefreshPolicy.MAX_AGE_MILLIS - 1, now, hasPrograms = true))
        assertFalse(
            IptvGuideRefreshPolicy.isFresh(
                now - IptvGuideRefreshPolicy.EMPTY_RETRY_MILLIS - 1,
                now,
                hasPrograms = false,
            ),
        )
    }
}
