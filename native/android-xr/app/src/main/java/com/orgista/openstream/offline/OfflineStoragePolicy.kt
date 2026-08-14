package com.orgista.openstream.offline

import android.content.Context
import android.os.Environment
import java.io.File

enum class OfflineStorageLocation {
    INTERNAL,
    SD_CARD,
}

data class OfflineStorageTarget(
    val location: OfflineStorageLocation,
    val directory: File,
    val removableAvailable: Boolean,
)

/** Selects app-specific device storage without broad media/file permissions. */
object OfflineStoragePolicy {
    fun effectiveLocation(
        requested: OfflineStorageLocation,
        removableAvailable: Boolean,
    ): OfflineStorageLocation = when {
        requested == OfflineStorageLocation.SD_CARD && removableAvailable -> OfflineStorageLocation.SD_CARD
        else -> OfflineStorageLocation.INTERNAL
    }

    fun canChange(downloadCount: Int): Boolean = downloadCount == 0

    fun requestedLocation(context: Context): OfflineStorageLocation {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LOCATION, OfflineStorageLocation.INTERNAL.name)
        return runCatching { OfflineStorageLocation.valueOf(raw.orEmpty()) }
            .getOrDefault(OfflineStorageLocation.INTERNAL)
    }

    fun saveRequestedLocation(context: Context, location: OfflineStorageLocation) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCATION, location.name)
            .apply()
    }

    fun removableRoot(context: Context): File? = context.getExternalFilesDirs(null)
        .filterNotNull()
        .firstOrNull { directory ->
            runCatching { Environment.isExternalStorageRemovable(directory) }.getOrDefault(false)
        }

    fun resolve(
        context: Context,
        requested: OfflineStorageLocation = requestedLocation(context),
    ): OfflineStorageTarget {
        val removable = removableRoot(context)
        val effective = effectiveLocation(requested, removable != null)
        // The legacy downloader used the primary app-specific external files
        // directory. On normal devices that is still built-in/emulated storage,
        // so retain it as INTERNAL to keep existing completed downloads valid.
        val root = if (effective == OfflineStorageLocation.SD_CARD) {
            removable!!
        } else {
            context.getExternalFilesDir(null) ?: context.filesDir
        }
        return OfflineStorageTarget(
            location = effective,
            directory = File(root, "offline-media").apply { mkdirs() },
            removableAvailable = removable != null,
        )
    }

    private const val PREFERENCES = "openstream.offline.storage"
    private const val KEY_LOCATION = "location.v1"
}
