package com.orgista.openstream.iptv

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

object IptvGuideClient {
    private const val MAX_COMPRESSED_BYTES = 20L * 1024L * 1024L
    private const val MAX_DECOMPRESSED_BYTES = 180L * 1024L * 1024L

    fun load(
        catalog: IptvSourceCatalog,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): List<IptvGuideProgram> {
        if (catalog.channels.isEmpty() || catalog.guideUrls.isEmpty()) return emptyList()
        return catalog.guideUrls.asSequence().flatMap { url ->
            val result = IptvGuideSourcePolicy.retryCandidates(url).firstNotNullOfOrNull { candidate ->
                runCatching { loadUrl(candidate, catalog.channels, windowStartMillis, windowEndMillis) }
                    .getOrNull()
            }.orEmpty()
            result.asSequence()
        }.distinctBy { "${it.channelKey}:${it.startMillis}:${it.title}" }.toList()
    }

    private fun loadUrl(
        url: String,
        channels: List<IptvChannel>,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): List<IptvGuideProgram> {
        require(IptvEndpointPolicy.isAllowed(url)) { "Guide address must use HTTP or HTTPS." }
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 25_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/xml, text/xml, application/gzip, application/octet-stream")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "OpenStream/0.1")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "Guide server returned HTTP $status." }
            val contentLength = connection.contentLengthLong
            require(contentLength < 0 || contentLength <= MAX_COMPRESSED_BYTES) { "Guide response is too large." }
            val boundedResponse = BoundedInputStream(connection.inputStream, MAX_COMPRESSED_BYTES)
            val decoded: InputStream = if (
                url.substringBefore('?').endsWith(".gz", ignoreCase = true) ||
                connection.contentEncoding.equals("gzip", ignoreCase = true)
            ) {
                GZIPInputStream(boundedResponse, 64 * 1024)
            } else {
                boundedResponse
            }
            return BoundedInputStream(decoded, MAX_DECOMPRESSED_BYTES).use { guide ->
                XmlTvParser.parse(guide, channels, windowStartMillis, windowEndMillis)
            }
        } finally {
            connection.disconnect()
        }
    }

    private class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) record(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) record(read.toLong())
            return read
        }

        private fun record(bytes: Long) {
            count += bytes
            if (count > limit) throw IOException("Guide response exceeded its size limit.")
        }
    }
}
