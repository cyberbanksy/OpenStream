package com.orgista.openstream.offline

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.orgista.openstream.data.ConnectionStore
import com.orgista.openstream.data.Smb
import com.orgista.openstream.player.SmbDataSource
import java.io.IOException

/** Lets Media3's persistent downloader read both add-on HTTP(S) and NAS SMB routes. */
@UnstableApi
class SchemeRoutingDataSource private constructor(private val context: Context) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "Data source is already open" }
        val route = dataSpec.uri.toString()
        val source = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true)) {
            val connection = ConnectionStore(context).nasConnections().firstOrNull { route.startsWith(Smb.shareUrl(it)) }
                ?: throw IOException("The NAS connection for this offline download is unavailable.")
            SmbDataSource.Factory(connection).createDataSource()
        } else {
            DefaultDataSource.Factory(context).createDataSource()
        }
        listeners.forEach(source::addTransferListener)
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: throw IOException("Data source is not open.")

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(context: Context) : DataSource.Factory {
        private val appContext = context.applicationContext
        override fun createDataSource(): DataSource = SchemeRoutingDataSource(appContext)
    }
}
