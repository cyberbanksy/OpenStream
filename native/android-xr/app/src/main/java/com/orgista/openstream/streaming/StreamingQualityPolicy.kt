package com.orgista.openstream.streaming

import com.orgista.openstream.data.StremioStream
import com.orgista.openstream.offline.OfflineDeviceProfile
import com.orgista.openstream.offline.OfflineDynamicRange
import com.orgista.openstream.offline.OfflineQualityPolicy
import com.orgista.openstream.offline.OfflineStreamDescriptor
import com.orgista.openstream.offline.OfflineVideoCodec
import java.util.Locale
import kotlin.math.ceil

data class StreamingReadiness(
    val sustainable: Boolean,
    val requiredCacheMinutes: Double,
    val readyWaitMinutes: Double,
)

object StreamingReadinessPolicy {
    fun assess(estimatedBitrateMbps: Double, bandwidthMbps: Double, runtimeMinutes: Int): StreamingReadiness {
        if (estimatedBitrateMbps <= 0.0 || bandwidthMbps <= 0.0 || runtimeMinutes <= 0) {
            return StreamingReadiness(false, 0.0, 0.0)
        }
        if (bandwidthMbps >= estimatedBitrateMbps) return StreamingReadiness(true, 0.0, 0.0)
        val cacheFraction = 1.0 - (bandwidthMbps / estimatedBitrateMbps)
        val cacheMinutes = runtimeMinutes * cacheFraction
        val waitMinutes = cacheMinutes * estimatedBitrateMbps / bandwidthMbps
        return StreamingReadiness(false, cacheMinutes, waitMinutes)
    }
}

data class StreamingPlan(
    val measuredMbps: Double,
    val estimatedBitrateMbps: Double,
    val sizeBytes: Long,
    val qualityLabel: String,
    val sustainable: Boolean,
    val downsizedForBandwidth: Boolean,
    val requiredCacheMinutes: Double,
    val readyWaitMinutes: Double,
    val startupBufferSeconds: Int,
    val statusText: String,
)

data class StreamingSelection(
    val selected: OfflineStreamDescriptor,
    val fallbacks: List<OfflineStreamDescriptor>,
    val plan: StreamingPlan,
)

/** Chooses the highest reliable route that fits both the device and measured internet throughput. */
object StreamingQualityPolicy {
    private const val HEADROOM = 0.80

    fun select(
        streams: List<StremioStream>,
        profile: OfflineDeviceProfile,
        runtimeMinutes: Int,
        bandwidthMbps: Double,
    ): StreamingSelection? {
        val compatible = OfflineQualityPolicy.compatible(streams, profile, Long.MAX_VALUE)
        if (compatible.isEmpty()) return null
        val bitrate = compatible.associateWith { estimateBitrateMbps(it, runtimeMinutes) }
        val reliable = compatible.filterNot { it.isLikelyUnsupportedDolbyVisionRemux(profile) }.ifEmpty { compatible }
        val sustainable = reliable.filter { bitrate.getValue(it) <= bandwidthMbps * HEADROOM }
        val selected = if (sustainable.isNotEmpty()) {
            sustainable.maxByOrNull { OfflineQualityPolicy.qualityScore(it, profile) }!!
        } else {
            reliable.minWithOrNull(
                compareBy<OfflineStreamDescriptor> { bitrate.getValue(it) }
                    .thenByDescending { OfflineQualityPolicy.qualityScore(it, profile) },
            )!!
        }
        val remaining = compatible.filter { it != selected }.sortedWith(
            compareBy<OfflineStreamDescriptor> { it.isLikelyUnsupportedDolbyVisionRemux(profile) }
                .thenBy { bitrate.getValue(it) > bandwidthMbps * HEADROOM }
                .thenByDescending { OfflineQualityPolicy.qualityScore(it, profile) },
        )
        val selectedBitrate = bitrate.getValue(selected)
        val readiness = StreamingReadinessPolicy.assess(
            selectedBitrate,
            bandwidthMbps * HEADROOM,
            runtimeMinutes,
        )
        val downsized = compatible.any {
            OfflineQualityPolicy.qualityScore(it, profile) > OfflineQualityPolicy.qualityScore(selected, profile)
        }
        val startupBuffer = when {
            !readiness.sustainable -> 30
            bandwidthMbps >= selectedBitrate * 2.0 -> 5
            bandwidthMbps >= selectedBitrate * 1.4 -> 10
            else -> 20
        }
        val size = sizeLabel(selected.sizeBytes)
        val speed = decimal(bandwidthMbps)
        val bitrateLabel = decimal(selectedBitrate)
        val status = when {
            readiness.sustainable && downsized ->
                "$speed Mbps measured · selected smaller $size ${selected.qualityLabel} · ${startupBuffer}s safety buffer"
            readiness.sustainable ->
                "$speed Mbps measured · $size ${selected.qualityLabel} selected · ${startupBuffer}s safety buffer"
            else ->
                "$speed Mbps measured · $size ${selected.qualityLabel} needs ~$bitrateLabel Mbps · cache " +
                    "${ceil(readiness.requiredCacheMinutes).toInt()} min (~${ceil(readiness.readyWaitMinutes).toInt()} min wait) or choose a smaller stream"
        }
        return StreamingSelection(
            selected = selected,
            fallbacks = remaining,
            plan = StreamingPlan(
                measuredMbps = bandwidthMbps,
                estimatedBitrateMbps = selectedBitrate,
                sizeBytes = selected.sizeBytes,
                qualityLabel = selected.qualityLabel,
                sustainable = readiness.sustainable,
                downsizedForBandwidth = downsized,
                requiredCacheMinutes = readiness.requiredCacheMinutes,
                readyWaitMinutes = readiness.readyWaitMinutes,
                startupBufferSeconds = startupBuffer,
                statusText = status,
            ),
        )
    }

    fun estimateBitrateMbps(descriptor: OfflineStreamDescriptor, runtimeMinutes: Int): Double {
        if (descriptor.sizeBytes > 0L && runtimeMinutes > 0) {
            return descriptor.sizeBytes.toDouble() * 8.0 / (runtimeMinutes * 60.0) / 1_000_000.0
        }
        val remux = Regex("(?i)\\bremux\\b").containsMatchIn(descriptor.stream.title)
        if (remux) return if ((descriptor.height ?: 0) >= 2160) 65.0 else 35.0
        val base = when (descriptor.height) {
            2160 -> if (descriptor.dynamicRange == OfflineDynamicRange.SDR) 22.0 else 28.0
            1440 -> 14.0
            1080 -> if (descriptor.dynamicRange == OfflineDynamicRange.SDR) 8.0 else 10.0
            720 -> 4.0
            576, 480 -> 2.0
            else -> 6.0
        }
        return when (descriptor.codec) {
            OfflineVideoCodec.HEVC, OfflineVideoCodec.AV1 -> base * 0.82
            else -> base
        }
    }

    private fun OfflineStreamDescriptor.isLikelyUnsupportedDolbyVisionRemux(
        profile: OfflineDeviceProfile,
    ): Boolean =
        dynamicRange == OfflineDynamicRange.DOLBY_VISION &&
            dolbyVisionProfile == null &&
            7 !in profile.dolbyVisionProfiles &&
            Regex("(?i)\\bremux\\b").containsMatchIn(stream.title)

    private fun decimal(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun sizeLabel(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> decimal(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB"
        bytes >= 1024L * 1024L -> decimal(bytes / (1024.0 * 1024.0)) + " MB"
        else -> "estimated"
    }
}
