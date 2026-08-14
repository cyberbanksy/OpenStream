package com.orgista.openstream.arr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ArrClientTest {
    @Test
    fun `private lan http and public https endpoints are accepted`() {
        assertTrue(ArrEndpointPolicy.isAllowed("http://192.168.1.20:7878"))
        assertTrue(ArrEndpointPolicy.isAllowed("http://10.0.0.5:8989/sonarr"))
        assertTrue(ArrEndpointPolicy.isAllowed("https://media.example.com/radarr"))
    }

    @Test
    fun `public cleartext and credential bearing urls are rejected`() {
        assertFalse(ArrEndpointPolicy.isAllowed("http://example.com:7878"))
        assertFalse(ArrEndpointPolicy.isAllowed("https://user:pass@example.com/radarr"))
    }

    @Test
    fun `status response is reduced to safe connection information`() {
        val status = ArrClient.parseStatus("""{"appName":"Radarr","version":"5.27.5","instanceName":"Movies"}""")
        assertEquals("Radarr", status.appName)
        assertEquals("5.27.5", status.version)
        assertEquals("Movies", status.instanceName)
    }

    @Test
    fun `connection string never exposes api key`() {
        val connection = ArrConnection(ArrKind.RADARR, "Movies", "https://media.example/radarr", "top-secret")
        assertFalse(connection.toString().contains("top-secret"))
    }

    @Test
    fun `private lan status check sends api key in header and parses response`() {
        ServerSocket(0).use { server ->
            val request = AtomicReference("")
            val worker = thread(start = true) {
                server.accept().use { socket ->
                    val lines = socket.getInputStream().bufferedReader().lineSequence().takeWhile { it.isNotEmpty() }.toList()
                    request.set(lines.joinToString("\n"))
                    val body = """{"appName":"Sonarr","version":"4.0","instanceName":"TV"}"""
                    socket.getOutputStream().bufferedWriter().use { output ->
                        output.write("HTTP/1.1 200 OK\r\n")
                        output.write("Content-Type: application/json\r\n")
                        output.write("Content-Length: ${body.toByteArray().size}\r\n")
                        output.write("Connection: close\r\n\r\n")
                        output.write(body)
                    }
                }
            }

            val status = ArrClient.test(
                ArrConnection(ArrKind.SONARR, "TV", "http://127.0.0.1:${server.localPort}", "test-only-key"),
            )
            worker.join(2_000)

            assertEquals("Sonarr", status.appName)
            assertTrue(request.get().contains("GET /api/v3/system/status HTTP/1.1"))
            assertTrue(request.get().contains("X-Api-Key: test-only-key"))
        }
    }
}
