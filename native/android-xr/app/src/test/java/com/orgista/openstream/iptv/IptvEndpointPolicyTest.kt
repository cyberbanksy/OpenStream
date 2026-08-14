package com.orgista.openstream.iptv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvEndpointPolicyTest {
    @Test
    fun `https and private network endpoints are accepted`() {
        assertTrue(IptvEndpointPolicy.isAllowed("https://tv.example.com/playlist.m3u8"))
        assertTrue(IptvEndpointPolicy.isAllowed("http://192.168.1.20:8080"))
        assertTrue(IptvEndpointPolicy.isAllowed("http://10.0.0.8/live.m3u"))
    }

    @Test
    fun `explicit provider http is accepted but credentials in urls are rejected`() {
        assertTrue(IptvEndpointPolicy.isAllowed("http://tv.example.com/playlist.m3u8"))
        assertFalse(IptvEndpointPolicy.isAllowed("https://person:secret@tv.example.com/live.m3u"))
        assertFalse(IptvEndpointPolicy.isAllowed("http://person:secret@tv.example.com/live.m3u"))
        assertFalse(IptvEndpointPolicy.isAllowed("file:///sdcard/list.m3u"))
    }
}
