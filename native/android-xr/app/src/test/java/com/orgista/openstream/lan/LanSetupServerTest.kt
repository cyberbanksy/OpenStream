package com.orgista.openstream.lan

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSetupServerTest {
    @Test
    fun `stopping setup while accept is blocked is a clean shutdown`() {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
        try {
            val server = LanSetupServer(addManifest = { "Test add-on" }, onAdded = {})
            server.start()
            server.close()
            Thread.sleep(1_200)

            assertTrue("Expected no worker-thread crash, got $uncaught", uncaught.isEmpty())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun `mobile setup page accepts a stremio manifest end to end`() {
        val added = CopyOnWriteArrayList<String>()
        val server = LanSetupServer(
            addManifest = { manifest ->
                added += manifest
                "Mock Catalog"
            },
            onAdded = {},
        )
        val session = server.start()
        try {
            assertEquals("/openstream", URI.create(session.url).path)
            assertEquals("openstream.local:${URI.create(session.url).port}", LanSetupPresentation.computerAddress(session))
            assertEquals(
                "${URI.create(session.url).host}:${URI.create(session.url).port}",
                LanSetupPresentation.fallbackComputerAddress(session),
            )
            val get = URL(session.url).openConnection() as HttpURLConnection
            assertEquals(200, get.responseCode)
            val page = get.inputStream.bufferedReader().use { it.readText() }
            assertTrue(page.contains("width=device-width,initial-scale=1"))
            assertTrue(page.contains("max-width:100%"))
            assertTrue(page.contains("OpenStream"))
            assertTrue(page.contains("Example: https://addon.example/manifest.json"))
            assertTrue(page.contains("Checking add-on"))
            assertTrue(page.contains("button{width:auto"))
            assertTrue(page.contains("class=\"brand-mark\""))
            assertTrue(page.contains("data:image/png;base64,"))
            assertTrue(page.contains("background:#000"))
            assertFalse(page.contains("gradient", ignoreCase = true))
            assertFalse(page.contains("#4d8dff", ignoreCase = true))
            assertFalse(page.contains("#347cf2", ignoreCase = true))
            assertFalse(page.contains("Access code", ignoreCase = true))

            val manifest = "https://addon.example/manifest.json"
            val body = "manifest=" +
                URLEncoder.encode(manifest, StandardCharsets.UTF_8.name())
            val post = URL(session.url).openConnection() as HttpURLConnection
            post.requestMethod = "POST"
            post.doOutput = true
            post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            post.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            assertEquals(200, post.responseCode)
            assertEquals(listOf(manifest), added)
            val success = post.inputStream.bufferedReader().use { it.readText() }
            assertTrue(success.contains("Connected to OpenStream"))
            assertTrue(success.contains("Mock Catalog"))
            assertTrue(success.contains("Added to Sources"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `short computer address opens and submits the setup page at root`() {
        val added = CopyOnWriteArrayList<String>()
        val server = LanSetupServer(
            addManifest = { manifest -> added += manifest; "Root Catalog" },
            onAdded = {},
        )
        val session = server.start()
        try {
            val numeric = URI.create(session.url)
            val rootUrl = URL("http://${numeric.host}:${numeric.port}/")
            val get = rootUrl.openConnection() as HttpURLConnection
            assertEquals(200, get.responseCode)
            val page = get.inputStream.bufferedReader().use { it.readText() }
            assertTrue(page.contains("WEB MANAGEMENT"))
            assertTrue(page.contains("Add a source"))

            val tokenMatch = Regex("""name="token" value="([^"]+)"""").find(page)
            val token = tokenMatch?.groupValues?.get(1).orEmpty()
            assertTrue("Expected session token in setup page", token.isNotBlank())

            val manifest = "https://root.example/manifest.json"
            val body = "manifest=" + URLEncoder.encode(manifest, StandardCharsets.UTF_8.name()) +
                "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.name())
            val post = rootUrl.openConnection() as HttpURLConnection
            post.requestMethod = "POST"
            post.doOutput = true
            post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            post.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            assertEquals(200, post.responseCode)
            assertEquals(listOf(manifest), added)
        } finally {
            server.close()
        }
    }

    @Test
    fun `unsupported manifest url returns a useful editable error page`() {
        val added = CopyOnWriteArrayList<String>()
        val server = LanSetupServer(
            addManifest = { manifest -> added += manifest; "Unexpected" },
            onAdded = {},
        )
        val session = server.start()
        try {
            val manifest = "http://addon.example/config.json"
            val body = "manifest=" + URLEncoder.encode(manifest, StandardCharsets.UTF_8.name())
            val post = URL(session.url).openConnection() as HttpURLConnection
            post.requestMethod = "POST"
            post.doOutput = true
            post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            post.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            assertEquals(400, post.responseCode)
            val error = post.errorStream.bufferedReader().use { it.readText() }
            assertTrue(error.contains("OpenStream"))
            assertTrue(error.contains("HTTP links are not supported"))
            assertTrue(added.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun `cross-origin request with untrusted Origin is rejected with 403`() {
        val added = CopyOnWriteArrayList<String>()
        val server = LanSetupServer(
            addManifest = { manifest -> added += manifest; "Should Not Add" },
            onAdded = {},
        )
        val session = server.start()
        try {
            val uri = URI.create(session.url)
            val manifest = "https://addon.example/manifest.json"
            val body = "manifest=" + URLEncoder.encode(manifest, StandardCharsets.UTF_8.name()) +
                "&token=" + URLEncoder.encode(session.token, StandardCharsets.UTF_8.name())
            java.net.Socket(uri.host, uri.port).use { socket ->
                val request = "POST ${uri.rawPath} HTTP/1.1\r\n" +
                    "Host: ${uri.host}:${uri.port}\r\n" +
                    "Origin: https://attacker.evil.com\r\n" +
                    "Content-Type: application/x-www-form-urlencoded\r\n" +
                    "Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n" +
                    "Connection: close\r\n\r\n" +
                    body
                socket.getOutputStream().write(request.toByteArray(StandardCharsets.UTF_8))
                socket.getOutputStream().flush()
                val responseLine = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine().orEmpty()
                assertTrue("Expected 403 status line, got $responseLine", responseLine.startsWith("HTTP/1.1 403"))
            }
            assertEquals(emptyList<String>(), added)
        } finally {
            server.close()
        }
    }

    @Test
    fun `cross-origin request with untrusted Referer is rejected with 403`() {
        val added = CopyOnWriteArrayList<String>()
        val server = LanSetupServer(
            addManifest = { manifest -> added += manifest; "Should Not Add" },
            onAdded = {},
        )
        val session = server.start()
        try {
            val manifest = "https://addon.example/manifest.json"
            val body = "manifest=" + URLEncoder.encode(manifest, StandardCharsets.UTF_8.name()) +
                "&token=" + URLEncoder.encode(session.token, StandardCharsets.UTF_8.name())
            val post = URL(session.url).openConnection() as HttpURLConnection
            post.requestMethod = "POST"
            post.doOutput = true
            post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            post.setRequestProperty("Referer", "http://evil.com/malicious-page.html")
            post.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            assertEquals(403, post.responseCode)
            assertTrue(added.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun `POST with invalid or missing session token is rejected with 403`() {
        val added = CopyOnWriteArrayList<String>()
        val server = LanSetupServer(
            addManifest = { manifest -> added += manifest; "Should Not Add" },
            onAdded = {},
        )
        val session = server.start()
        try {
            val numeric = URI.create(session.url)
            val rootUrl = URL("http://${numeric.host}:${numeric.port}/")
            val manifest = "https://addon.example/manifest.json"
            val body = "manifest=" + URLEncoder.encode(manifest, StandardCharsets.UTF_8.name()) +
                "&token=invalid-fake-token-12345"
            val post = rootUrl.openConnection() as HttpURLConnection
            post.requestMethod = "POST"
            post.doOutput = true
            post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            post.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            assertEquals(403, post.responseCode)
            assertTrue(added.isEmpty())
        } finally {
            server.close()
        }
    }
}
