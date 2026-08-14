package com.orgista.openstream.offline

import java.util.Locale
import kotlin.math.roundToInt

/** Repairs Media3/provider progress values and keeps download copy honest. */
object OfflineProgressPolicy {
    private const val UINT32_SIZE = 1L shl 32

    fun contentLength(reportedLength: Long, bytesDownloaded: Long, completed: Boolean): Long {
        val repaired = when {
            reportedLength >= 0L -> reportedLength
            reportedLength < -1L && reportedLength >= Int.MIN_VALUE.toLong() -> reportedLength + UINT32_SIZE
            else -> -1L
        }
        return if (completed) maxOf(repaired, bytesDownloaded) else repaired
    }

    fun percent(
        reportedPercent: Float,
        bytesDownloaded: Long,
        contentLength: Long,
        completed: Boolean,
    ): Int? = when {
        completed -> 100
        reportedPercent.isFinite() && reportedPercent >= 0f -> reportedPercent.roundToInt().coerceIn(0, 100)
        contentLength > 0L -> ((bytesDownloaded.toDouble() / contentLength.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 99)
        else -> null
    }

    fun progressLabel(percent: Int?, bytesDownloaded: Long): String = if (percent != null) {
        "$percent% downloaded · ${formatBytes(bytesDownloaded)} saved"
    } else {
        "Downloading · ${formatBytes(bytesDownloaded)} saved"
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L).toDouble()
        val gib = 1024.0 * 1024.0 * 1024.0
        val mib = 1024.0 * 1024.0
        return if (safe >= gib) {
            String.format(Locale.US, "%.1f GB", safe / gib)
        } else {
            "${(safe / mib).roundToInt()} MB"
        }
    }
}
