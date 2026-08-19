package com.orgista.openstream.lan

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSetupProtocolTest {
    @Test
    fun `only local network clients are accepted`() {
        assertTrue(LanSetupProtocol.isAllowedClient(InetAddress.getByName("192.168.1.50")))
        assertTrue(LanSetupProtocol.isAllowedClient(InetAddress.getByName("10.0.0.8")))
        assertTrue(LanSetupProtocol.isAllowedClient(InetAddress.getLoopbackAddress()))
        assertFalse(LanSetupProtocol.isAllowedClient(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun `form input is decoded without an access code`() {
        val form = LanSetupProtocol.parseForm("manifest=https%3A%2F%2Faddon.example%2Fmanifest.json")

        assertEquals("https://addon.example/manifest.json", form["manifest"])
        assertFalse(form.containsKey("code"))
    }

    @Test
    fun `manifest url validation explains the unsupported part`() {
        assertEquals(
            "Add https:// at the beginning. Example: https://addon.example/manifest.json",
            LanSetupProtocol.manifestUrlError("addon.example/manifest.json"),
        )
        assertEquals(
            "HTTP links are not supported. Use the add-on's HTTPS manifest URL.",
            LanSetupProtocol.manifestUrlError("http://addon.example/manifest.json"),
        )
        assertEquals(
            "stremio:// links are not supported here. Paste the add-on's HTTPS manifest URL.",
            LanSetupProtocol.manifestUrlError("stremio://addon.example/manifest.json"),
        )
        assertEquals(
            "This URL points to config.json. Paste a manifest.json URL or the add-on's base URL.",
            LanSetupProtocol.manifestUrlError("https://addon.example/config.json"),
        )
        assertEquals(
            "URLs containing a username or password are not supported.",
            LanSetupProtocol.manifestUrlError("https://user:password@addon.example/manifest.json"),
        )
        assertNull(LanSetupProtocol.manifestUrlError("https://addon.example/config/manifest.json"))
        assertNull(LanSetupProtocol.manifestUrlError("https://addon.example/private-config"))
    }

    @Test
    fun `setup QR opens the short lived local page`() {
        val session = LanSetupSession(
            url = "http://192.168.1.50:11470/openstream",
            expiresAtMillis = 1_000L,
        )

        assertEquals(session.url, LanSetupPresentation.qrContent(session))
        assertEquals("openstream.local:11470", LanSetupPresentation.computerAddress(session))
        assertEquals("http://openstream.local:11470/", LanSetupPresentation.computerUrl(session))
        assertEquals("192.168.1.50:11470", LanSetupPresentation.fallbackComputerAddress(session))
        val matrix = LanSetupQrCode.matrix(LanSetupPresentation.qrContent(session), pixels = 192)
        assertEquals(192, matrix.width)
        assertEquals(192, matrix.height)
        assertTrue((0 until matrix.height).any { y -> (0 until matrix.width).any { x -> matrix[x, y] } })
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))),
        )
        assertEquals(session.url, decoded.text)
    }

    @Test
    fun `isValidOrigin allows valid local origins and ports`() {
        assertTrue(LanSetupProtocol.isValidOrigin("http://openstream.local:11470", "192.168.1.50", 11470))
        assertTrue(LanSetupProtocol.isValidOrigin("http://localhost:11470", "192.168.1.50", 11470))
        assertTrue(LanSetupProtocol.isValidOrigin("http://127.0.0.1:11470", "192.168.1.50", 11470))
        assertTrue(LanSetupProtocol.isValidOrigin("http://192.168.1.50:11470", "192.168.1.50", 11470))
        assertTrue(LanSetupProtocol.isValidOrigin(null, "192.168.1.50", 11470))
    }

    @Test
    fun `isValidOrigin rejects external domains, null origins, and mismatched ports`() {
        assertFalse(LanSetupProtocol.isValidOrigin("https://evil.com", "192.168.1.50", 11470))
        assertFalse(LanSetupProtocol.isValidOrigin("http://attacker.local:11470", "192.168.1.50", 11470))
        assertFalse(LanSetupProtocol.isValidOrigin("null", "192.168.1.50", 11470))
        assertFalse(LanSetupProtocol.isValidOrigin("http://192.168.1.50:8080", "192.168.1.50", 11470))
        assertFalse(LanSetupProtocol.isValidOrigin("ftp://192.168.1.50:11470", "192.168.1.50", 11470))
    }

    @Test
    fun `parseForm safely handles malformed percent encodings`() {
        val form = LanSetupProtocol.parseForm("manifest=https%3A%2F%2Faddon.example%2Fmanifest%&invalid=%ZZ")
        assertEquals("https%3A%2F%2Faddon.example%2Fmanifest%", form["manifest"])
        assertEquals("%ZZ", form["invalid"])
    }

    @Test
    fun `generateSessionToken produces unique random tokens`() {
        val token1 = LanSetupProtocol.generateSessionToken()
        val token2 = LanSetupProtocol.generateSessionToken()
        assertTrue(token1.isNotBlank())
        assertTrue(token2.isNotBlank())
        assertEquals(32, token1.length)
        assertFalse(token1 == token2)
    }
}
