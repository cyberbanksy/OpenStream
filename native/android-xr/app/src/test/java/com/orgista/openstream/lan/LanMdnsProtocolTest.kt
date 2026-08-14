package com.orgista.openstream.lan

import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanMdnsProtocolTest {
    @Test
    fun `answers an IPv4 query for the setup host`() {
        val query = queryFor("openstream.local", type = 1)

        val response = LanMdnsProtocol.responseFor(
            query = query,
            host = "openstream.local",
            address = InetAddress.getByName("192.168.1.87").address,
        )

        assertNotNull(response)
        val bytes = requireNotNull(response)
        assertTrue(bytes.size > 24)
        assertTrue(bytes[2].toInt() and 0x80 != 0) // response
        assertTrue(bytes[2].toInt() and 0x04 != 0) // authoritative
        assertArrayEquals(byteArrayOf(0, 1), bytes.copyOfRange(6, 8))
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 87), bytes.takeLast(4).toByteArray())
    }

    @Test
    fun `answers an ANY query but ignores other names and malformed packets`() {
        assertNotNull(
            LanMdnsProtocol.responseFor(
                queryFor("OpenStream.Local.", type = 255),
                "openstream.local",
                byteArrayOf(10, 0, 0, 2),
            ),
        )
        assertNull(
            LanMdnsProtocol.responseFor(
                queryFor("another.local", type = 1),
                "openstream.local",
                byteArrayOf(10, 0, 0, 2),
            ),
        )
        assertNull(LanMdnsProtocol.responseFor(byteArrayOf(0, 1), "openstream.local", byteArrayOf(10, 0, 0, 2)))
        assertNull(LanMdnsProtocol.responseFor(queryFor("openstream.local", 28), "openstream.local", byteArrayOf(10, 0, 0, 2)))
        assertNull(LanMdnsProtocol.responseFor(queryFor("openstream.local", 1), "openstream.local", byteArrayOf(10, 0, 0)))
    }

    @Test
    fun `host matcher rejects misleading suffixes`() {
        assertTrue(LanMdnsProtocol.isSupportedHost("openstream.local", "OPENSTREAM.local."))
        assertFalse(LanMdnsProtocol.isSupportedHost("openstream.local", "openstream.local.example"))
    }

    private fun queryFor(host: String, type: Int): ByteArray {
        val output = ArrayList<Byte>()
        repeat(12) { output += 0 }
        output[5] = 1 // one question
        host.trimEnd('.').split('.').forEach { label ->
            output += label.length.toByte()
            label.encodeToByteArray().forEach(output::add)
        }
        output += 0
        output += ((type ushr 8) and 0xff).toByte()
        output += (type and 0xff).toByte()
        output += 0
        output += 1
        return output.toByteArray()
    }
}
