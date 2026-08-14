package com.orgista.openstream.arr

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrDownloadPreferenceTest {
    @Test
    fun `unknown or missing preference keeps the safe ask behavior`() {
        assertEquals(ArrDownloadDestination.ASK, ArrDownloadDestination.fromStored(null))
        assertEquals(ArrDownloadDestination.ASK, ArrDownloadDestination.fromStored("future-value"))
    }

    @Test
    fun `device and server preferences round trip from private storage values`() {
        assertEquals(ArrDownloadDestination.DEVICE, ArrDownloadDestination.fromStored("device"))
        assertEquals(ArrDownloadDestination.SERVER, ArrDownloadDestination.fromStored("server"))
    }
}
