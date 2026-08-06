package com.orgista.openstream.offline

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.orgista.openstream.NativeAppActivity
import com.orgista.openstream.R
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.StremioStream
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
data class OfflineEntry(
    val downloadId: String,
    val item: MediaItem,
    val state: Int,
    val percent: Int?,
    val bytesDownloaded: Long,
    val contentLength: Long,
) {
    val completed: Boolean get() = state == Download.STATE_COMPLETED
}

/** Process-wide Media3 download/cache graph, backed by app-specific device storage. */
@UnstableApi
class OfflineMediaRuntime private constructor(private val context: Context) {
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val cacheDirectory = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "offline-media",
    ).apply { mkdirs() }
    val cache = SimpleCache(cacheDirectory, NoOpCacheEvictor(), databaseProvider)
    val upstreamFactory: DataSource.Factory = SchemeRoutingDataSource.Factory(context)
    private val executor = Executors.newFixedThreadPool(3)
    val downloadManager = DownloadManager(context, databaseProvider, cache, upstreamFactory, executor).apply {
        maxParallelDownloads = 2
        minRetryCount = 4
        requirements = Requirements(Requirements.NETWORK or Requirements.DEVICE_STORAGE_NOT_LOW)
    }
    val notificationHelper = DownloadNotificationHelper(context, DOWNLOAD_CHANNEL_ID)
    private val mutableEntries = MutableStateFlow<List<OfflineEntry>>(emptyList())
    val entries: StateFlow<List<OfflineEntry>> = mutableEntries.asStateFlow()

    init {
        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) = refresh()

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?,
                ) = refresh()

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh()
            },
        )
        refresh()
    }

    fun completedItems(): List<MediaItem> = entries.value.filter(OfflineEntry::completed).map(OfflineEntry::item)

    fun entryForMediaId(mediaId: String): OfflineEntry? = entries.value.firstOrNull { it.item.id == mediaId }

    fun downloadMediaItem(downloadId: String): Media3Item? =
        runCatching { downloadManager.downloadIndex.getDownload(downloadId)?.request?.toMediaItem() }.getOrNull()

    fun cacheOnlyDataSourceFactory(): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setCacheReadDataSourceFactory(FileDataSource.Factory())
        .setCacheWriteDataSinkFactory(null)
        .setUpstreamDataSourceFactory(null)

    fun remove(downloadId: String) {
        DownloadService.sendRemoveDownload(context, OpenStreamDownloadService::class.java, downloadId, false)
    }

    suspend fun enqueue(item: MediaItem, streams: List<StremioStream>): OfflineStreamDescriptor {
        val profile = OfflineDeviceCapabilities.inspect(context)
        val reserve = 256L * 1024L * 1024L
        val available = (StatFs(cacheDirectory.absolutePath).availableBytes - reserve).coerceAtLeast(0L)
        val selected = OfflineQualityPolicy.select(streams, profile, available)
            ?: throw IOException("No compatible stream fits the available device storage.")
        val data = OfflineMetadata.encode(item, selected)
        val request = buildDownloadRequest(downloadId(item.id), selected.stream.url, data, profile)
        DownloadService.sendAddDownload(context, OpenStreamDownloadService::class.java, request, true)
        return selected
    }

    private suspend fun buildDownloadRequest(
        id: String,
        uri: String,
        data: ByteArray,
        profile: OfflineDeviceProfile,
    ): DownloadRequest {
        val mimeType = adaptiveMimeType(uri)
        if (mimeType == null) {
            return DownloadRequest.Builder(id, Uri.parse(uri))
                .setCustomCacheKey("offline-$id")
                .setData(data)
                .build()
        }

        val parameters = DownloadHelper.getDefaultTrackSelectorParameters(context)
            .buildUpon()
            .setMaxVideoSize(profile.maxWidth, profile.maxHeight)
            .setViewportSize(profile.maxWidth, profile.maxHeight, true)
            .build()
        val mediaItem = Media3Item.Builder().setUri(uri).setMimeType(mimeType).build()
        val renderers = NextRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val helper = DownloadHelper.forMediaItem(mediaItem, parameters, renderers, upstreamFactory)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { helper.release() }
            helper.prepare(
                object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper, tracksInfoIsIncomplete: Boolean) {
                        try {
                            for (period in 0 until helper.periodCount) {
                                helper.replaceTrackSelections(period, parameters)
                            }
                            continuation.resume(helper.getDownloadRequest(id, data))
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        } finally {
                            helper.release()
                        }
                    }

                    override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                        helper.release()
                        continuation.resumeWithException(error)
                    }
                },
            )
        }
    }

    private fun refresh() {
        mutableEntries.value = runCatching {
            buildList {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        val item = OfflineMetadata.decode(download) ?: continue
                        add(
                            OfflineEntry(
                                downloadId = download.request.id,
                                item = item,
                                state = download.state,
                                percent = download.percentDownloaded.takeIf { it >= 0f }?.toInt()?.coerceIn(0, 100),
                                bytesDownloaded = download.bytesDownloaded,
                                contentLength = download.contentLength,
                            ),
                        )
                    }
                }
            }.sortedByDescending { it.completed }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "openstream.offline.downloads"
        const val DOWNLOAD_NOTIFICATION_ID = 4701

        @Volatile private var instance: OfflineMediaRuntime? = null

        fun get(context: Context): OfflineMediaRuntime = instance ?: synchronized(this) {
            instance ?: OfflineMediaRuntime(context.applicationContext).also { instance = it }
        }

        private fun downloadId(mediaId: String): String = MessageDigest.getInstance("SHA-256")
            .digest(mediaId.toByteArray())
            .joinToString("") { "%02x".format(it) }

        private fun adaptiveMimeType(uri: String): String? {
            val path = uri.substringBefore('?').lowercase()
            return when {
                path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                path.endsWith(".ism") || path.endsWith(".isml") -> MimeTypes.APPLICATION_SS
                else -> null
            }
        }
    }
}

@OptIn(UnstableApi::class)
class OpenStreamDownloadService : DownloadService(
    OfflineMediaRuntime.DOWNLOAD_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    OfflineMediaRuntime.DOWNLOAD_CHANNEL_ID,
    R.string.offline_download_channel_name,
    R.string.offline_download_channel_description,
) {
    override fun getDownloadManager(): DownloadManager = OfflineMediaRuntime.get(this).downloadManager

    override fun getScheduler() = null

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int) =
        OfflineMediaRuntime.get(this).notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, NativeAppActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
            null,
            downloads,
            notMetRequirements,
        )
}
