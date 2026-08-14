package com.orgista.openstream.offline

enum class OfflineDownloadVisual { DOWNLOAD, CHECK, PROGRESS }

object OfflineDownloadUiPolicy {
    fun visual(completed: Boolean, active: Boolean, preparing: Boolean): OfflineDownloadVisual = when {
        completed -> OfflineDownloadVisual.CHECK
        active || preparing -> OfflineDownloadVisual.PROGRESS
        else -> OfflineDownloadVisual.DOWNLOAD
    }

    fun canQueue(completed: Boolean, active: Boolean, preparing: Boolean): Boolean =
        !completed && !active && !preparing
}

object OfflineRetentionPolicy {
    fun shouldDelete(
        deleteAfterWatched: Boolean,
        playbackEnded: Boolean,
        offlineDownloadId: String?,
    ): Boolean = deleteAfterWatched && playbackEnded && !offlineDownloadId.isNullOrBlank()
}

object OfflineBulkDeletePolicy {
    fun canStart(downloadCount: Int, removing: Boolean): Boolean = downloadCount > 0 && !removing

    fun shouldRemove(
        confirming: Boolean,
        downloadCount: Int,
        removing: Boolean,
    ): Boolean = confirming && canStart(downloadCount, removing)
}
