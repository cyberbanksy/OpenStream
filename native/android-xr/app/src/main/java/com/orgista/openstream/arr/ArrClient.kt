package com.orgista.openstream.arr

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

object ArrClient {
    fun test(connection: ArrConnection): ArrStatus {
        return parseStatus(request(connection, "GET", "/api/v3/system/status"))
    }

    fun parseStatus(body: String): ArrStatus {
        val json = JSONObject(body)
        return ArrStatus(
            appName = json.optString("appName").ifBlank { "ARR" }.take(80),
            version = json.optString("version").take(40),
            instanceName = json.optString("instanceName").take(80),
        )
    }

    internal fun request(
        connection: ArrConnection,
        method: String,
        path: String,
        body: String? = null,
    ): String {
        require(ArrEndpointPolicy.isAllowed(connection.baseUrl)) { "Use HTTPS, or a private local IP address for HTTP." }
        require(connection.apiKey.isNotBlank()) { "API key is required." }
        require(method in setOf("GET", "POST", "PUT")) { "Unsupported ARR request." }
        require(path.startsWith("/api/v3/")) { "Unsupported ARR path." }
        val endpoint = "${ArrEndpointPolicy.normalize(connection.baseUrl)}$path"
        return if (endpoint.startsWith("https://")) {
            httpsRequest(endpoint, connection.apiKey, method, body)
        } else {
            privateHttpRequest(endpoint, connection.apiKey, method, body)
        }
    }

    private fun httpsRequest(endpoint: String, apiKey: String, method: String, body: String?): String {
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Api-Key", apiKey)
        connection.setRequestProperty("User-Agent", "OpenStream/0.1 ARR")
        if (body != null) {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(bytes) }
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("${method.lowercase().replaceFirstChar(Char::uppercase)} request returned HTTP $status.")
            connection.inputStream.bufferedReader().use { it.readText().take(256_000) }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Android intentionally blocks broad cleartext traffic. This bounded client
     * supports the common private-IP ARR setup without enabling HTTP for the
     * rest of the app. Redirects and public hosts are never followed.
     */
    private fun privateHttpRequest(endpoint: String, apiKey: String, method: String, body: String?): String {
        val uri = URI(endpoint)
        if (!ArrEndpointPolicy.isPrivateLiteral(uri.host)) throw IOException("Local HTTP requires a private IP address.")
        val port = if (uri.port > 0) uri.port else 80
        val path = buildString {
            append(uri.rawPath.ifBlank { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
        return Socket(uri.host, port).use { socket ->
            socket.soTimeout = 15_000
            val bodyBytes = body?.toByteArray(StandardCharsets.UTF_8)
            val headers = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: ${uri.host}:$port\r\n")
                append("Accept: application/json\r\n")
                append("X-Api-Key: $apiKey\r\n")
                append("User-Agent: OpenStream/0.1 ARR\r\n")
                if (bodyBytes != null) {
                    append("Content-Type: application/json; charset=utf-8\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                }
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
            socket.getOutputStream().apply {
                write(headers)
                if (bodyBytes != null) write(bodyBytes)
                flush()
            }

            val raw = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            val input = socket.getInputStream()
            while (raw.size() <= 300_000) {
                val count = input.read(buffer)
                if (count < 0) break
                raw.write(buffer, 0, count)
            }
            val response = raw.toString(StandardCharsets.UTF_8.name())
            val headEnd = response.indexOf("\r\n\r\n")
            if (headEnd < 0) throw IOException("ARR returned an invalid response.")
            val status = response.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
                ?: throw IOException("ARR returned an invalid status.")
            if (status !in 200..299) throw IOException("${method.lowercase().replaceFirstChar(Char::uppercase)} request returned HTTP $status.")
            decodeHttpBody(response.substring(0, headEnd), response.substring(headEnd + 4))
        }
    }

    private fun decodeHttpBody(headers: String, body: String): String {
        if (!headers.contains("transfer-encoding: chunked", ignoreCase = true)) return body
        val result = StringBuilder()
        var cursor = 0
        while (cursor < body.length) {
            val lineEnd = body.indexOf("\r\n", cursor)
            if (lineEnd < 0) break
            val size = body.substring(cursor, lineEnd).substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            val start = lineEnd + 2
            val end = (start + size).coerceAtMost(body.length)
            result.append(body, start, end)
            cursor = end + 2
        }
        return result.toString()
    }
}
