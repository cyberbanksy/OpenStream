package com.orgista.openstream.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMeasurementPolicyTest {
    @Test
    fun `cellular playback measures automatically`() {
        assertTrue(NetworkMeasurementPolicy.shouldMeasureBeforePlayback(NetworkTransport.CELLULAR, cached = false))
    }

    @Test
    fun `wifi and ethernet do not downshift from a tiny preflight sample`() {
        assertFalse(NetworkMeasurementPolicy.shouldMeasureBeforePlayback(NetworkTransport.WIFI, cached = false))
        assertFalse(NetworkMeasurementPolicy.shouldMeasureBeforePlayback(NetworkTransport.ETHERNET, cached = false))
    }

    @Test
    fun `cellular probe ramps within a bounded data budget`() {
        assertEquals(listOf(100_000, 500_000, 2_000_000, 5_000_000), NetworkMeasurementPolicy.sampleSizes(NetworkTransport.CELLULAR))
    }

    @Test
    fun `ramped probe ignores handshake dominated first sample`() {
        assertEquals(240.0, NetworkMeasurementPolicy.representativeMbps(listOf(8.0, 220.0, 240.0, 260.0)), 0.01)
    }

    @Test
    fun `cloudflare access failure retries with browser compatible request`() {
        val profiles = BandwidthProbeRequestPolicy.profiles(nowMillis = 1234L)

        assertEquals(2, profiles.size)
        assertEquals("application/octet-stream", profiles.first().headers["Accept"])
        assertEquals("*/*", profiles.last().headers["Accept"])
        assertEquals("https://speed.cloudflare.com/", profiles.last().headers["Referer"])
        assertTrue(profiles.last().querySuffix.contains("1234"))
        assertTrue(BandwidthProbeRequestPolicy.shouldRetry(403))
        assertFalse(BandwidthProbeRequestPolicy.shouldRetry(404))
    }
}
