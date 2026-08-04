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

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            transferInitializing(dataSpec)
            val smbFile = SmbFile(dataSpec.uri.toString(), Smb.context(connection))
            val access = SmbRandomAccessFile(smbFile, "r")
            file = access
            if (dataSpec.position > 0) access.seek(dataSpec.position)
            val length = smbFile.length()
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                length - dataSpec.position
            }
            if (bytesRemaining < 0) throw IOException("SMB read position is past the end of the file.")
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: Exception) {
            throw IOException("SMB open failed: ${error.message}", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val read = try {
            file?.read(buffer, offset, toRead) ?: -1
        } catch (error: Exception) {
            throw IOException("SMB read failed: ${error.message}", error)
        }
        if (read < 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
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
