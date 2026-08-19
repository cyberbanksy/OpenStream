package com.orgista.openstream.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.orgista.openstream.data.NasConnection
import com.orgista.openstream.data.Smb
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException

/**
 * Media3 DataSource that reads smb:// URIs through jcifs-ng with random
 * access, so ExoPlayer can seek inside NAS files exactly like local ones.
 * Credentials come from the active connection, never from the URI.
 */
@UnstableApi
class SmbDataSource(private val connection: NasConnection) : BaseDataSource(true) {
    private var file: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var loggedFirstRead = false

    // Read-ahead buffer. ExoPlayer's extractors read a few bytes at a time
    // while parsing container structure; over SMB each read is a network round
    // trip, so we fill a large buffer per SMB read and serve the small reads
    // from memory. Without this, parsing a multi-GB MKV takes minutes.
    private val readAhead = ByteArray(256 * 1024)
    private var bufferPos = 0
    private var bufferLen = 0

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            val uriStr = dataSpec.uri.toString()
            if (!SmbHostPolicy.isHostAllowed(uriStr, connection.host)) {
                throw SecurityException(
                    "SMB host mismatch or invalid scheme: requested '$uriStr' does not match configured NAS host '${connection.host}'"
                )
            }
            val smbFile = SmbFile(dataSpec.uri.toString(), Smb.context(connection))
            val length = smbFile.length()
            val access = SmbRandomAccessFile(smbFile, "r")
            file = access
            if (dataSpec.position > 0) access.seek(dataSpec.position)
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                length - dataSpec.position
            }
            if (bytesRemaining < 0) throw IOException("SMB read position is past the end of the file.")
            bufferPos = 0
            bufferLen = 0
            loggedFirstRead = false
            com.orgista.openstream.Logs.event("smb open ok: pos=${dataSpec.position} len=$length remaining=$bytesRemaining")
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: Exception) {
            com.orgista.openstream.Logs.error("smb open failed (${error::class.simpleName})")
            throw IOException("SMB open failed: ${error.message}", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        if (bufferPos >= bufferLen) {
            // Refill: one large SMB read serves many small extractor reads.
            val toRead = minOf(readAhead.size.toLong(), bytesRemaining).toInt()
            val startedAt = System.currentTimeMillis()
            val filled = try {
                file?.read(readAhead, 0, toRead) ?: -1
            } catch (error: Exception) {
                com.orgista.openstream.Logs.error("smb read failed (${error::class.simpleName})")
                throw IOException("SMB read failed: ${error.message}", error)
            }
            if (!loggedFirstRead) {
                loggedFirstRead = true
                com.orgista.openstream.Logs.event("smb refill: asked=$toRead got=$filled in ${System.currentTimeMillis() - startedAt}ms")
            }
            if (filled <= 0) return C.RESULT_END_OF_INPUT
            bufferPos = 0
            bufferLen = filled
        }

        val available = bufferLen - bufferPos
        val toCopy = minOf(length, available)
        System.arraycopy(readAhead, bufferPos, buffer, offset, toCopy)
        bufferPos += toCopy
        bytesRemaining -= toCopy
        bytesTransferred(toCopy)
        return toCopy
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            file?.close()
        } catch (_: Exception) {
            // Closing a dropped connection must not mask the original error.
        } finally {
            file = null
            transferEnded()
        }
    }

    @UnstableApi
    class Factory(private val connection: NasConnection) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(connection)
    }
}

object SmbHostPolicy {
    fun isHostAllowed(uriString: String, expectedHost: String): Boolean {
        val uri = runCatching { java.net.URI(uriString) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "smb") return false
        val host = uri.host?.lowercase() ?: return false
        return host.isNotBlank() && host.equals(expectedHost.lowercase(), ignoreCase = true)
    }
}
