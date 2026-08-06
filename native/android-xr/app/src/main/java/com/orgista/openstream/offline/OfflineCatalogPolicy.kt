package com.orgista.openstream.offline

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.StremioCatalogMerger

object OfflineCatalogPolicy {
    /** Offline state is intentionally strict: only fully downloaded items remain visible. */
    fun select(isOnline: Boolean, online: List<MediaItem>, offline: List<MediaItem>): List<MediaItem> =
        if (isOnline) StremioCatalogMerger.merge(offline, online) else offline
}
