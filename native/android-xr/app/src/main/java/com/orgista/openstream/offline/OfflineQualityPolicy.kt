package com.orgista.openstream.offline

import com.orgista.openstream.data.StremioStream
import kotlin.math.roundToLong

enum class OfflineVideoCodec { AVC, HEVC, AV1, UNKNOWN }

enum class OfflineDynamicRange { SDR, HDR10, HDR10_PLUS, HLG, DOLBY_VISION }

data class OfflineDeviceProfile(
    val maxWidth: Int,
    val maxHeight: Int,
    val codecs: Set<OfflineVideoCodec>,
    val dynamicRanges: Set<OfflineDynamicRange>,
    val dolbyAtmos: Boolean,
)

data class OfflineStreamDescriptor(
    val stream: StremioStream,
    val width: Int?,
    val height: Int?,
    val codec: OfflineVideoCodec,
    val dynamicRange: OfflineDynamicRange,
    val dolbyAtmos: Boolean,
    val sizeBytes: Long,
) {
    val qualityLabel: String
        get() = buildList {
            height?.let { add(if (it >= 2160) "4K" else "${it}p") }
            if (dynamicRange != OfflineDynamicRange.SDR) {
                add(
                    when (dynamicRange) {
                        OfflineDynamicRange.DOLBY_VISION -> "Dolby Vision"
                        OfflineDynamicRange.HDR10_PLUS -> "HDR10+"
                        OfflineDynamicRange.HDR10 -> "HDR10"
                        OfflineDynamicRange.HLG -> "HLG"
                        OfflineDynamicRange.SDR -> "SDR"
                    },
                )
            }
            if (codec != OfflineVideoCodec.UNKNOWN) add(codec.name)
            if (dolbyAtmos) add("Dolby Atmos")
        }.ifEmpty { listOf("Best available") }.joinToString(" · ")

    companion object {
        private val sizePattern = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(TB|GB|MB)")

        fun parse(stream: StremioStream): OfflineStreamDescriptor {
            val text = stream.title
            val height = when {
                Regex("(?i)(?:2160p|\\b4k\\b)").containsMatchIn(text) -> 2160
                Regex("(?i)1440p").containsMatchIn(text) -> 1440
                Regex("(?i)1080p").containsMatchIn(text) -> 1080
                Regex("(?i)720p").containsMatchIn(text) -> 720
                Regex("(?i)576p").containsMatchIn(text) -> 576
                Regex("(?i)480p").containsMatchIn(text) -> 480
                else -> null
            }
            val width = when (height) {
                2160 -> 3840
                1440 -> 2560
                1080 -> 1920
                720 -> 1280
                576 -> 1024
                480 -> 854
                else -> null
            }
            val codec = when {
                Regex("(?i)(?:\\bav1\\b|av01)").containsMatchIn(text) -> OfflineVideoCodec.AV1
                Regex("(?i)(?:\\bhevc\\b|h[ .]?265|x265|hev1|hvc1)").containsMatchIn(text) -> OfflineVideoCodec.HEVC
                Regex("(?i)(?:\\bavc\\b|h[ .]?264|x264|avc1)").containsMatchIn(text) -> OfflineVideoCodec.AVC
                else -> OfflineVideoCodec.UNKNOWN
            }
            val range = when {
                Regex("(?i)(?:dolby[ ._-]*vision|\\bdv\\b|dvhe|dvh1)").containsMatchIn(text) ->
                    OfflineDynamicRange.DOLBY_VISION
                Regex("(?i)hdr[ ._-]*10\\+").containsMatchIn(text) -> OfflineDynamicRange.HDR10_PLUS
                Regex("(?i)hdr[ ._-]*10|\\bhdr\\b").containsMatchIn(text) -> OfflineDynamicRange.HDR10
                Regex("(?i)\\bhlg\\b").containsMatchIn(text) -> OfflineDynamicRange.HLG
                else -> OfflineDynamicRange.SDR
            }
            val atmos = Regex("(?i)(?:dolby[ ._-]*atmos|\\batmos\\b|eac3[ ._-]*joc)").containsMatchIn(text)
            val size = sizePattern.find(text)?.let { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: return@let 0L
                val multiplier = when (match.groupValues[2].uppercase()) {
                    "TB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
                    "GB" -> 1024.0 * 1024.0 * 1024.0
                    else -> 1024.0 * 1024.0
                }
                (value * multiplier).roundToLong()
            } ?: 0L
            return OfflineStreamDescriptor(stream, width, height, codec, range, atmos, size)
        }
    }
}

object OfflineQualityPolicy {
    fun select(
        streams: List<StremioStream>,
        profile: OfflineDeviceProfile,
        availableBytes: Long,
    ): OfflineStreamDescriptor? = streams
        .asSequence()
        .map(OfflineStreamDescriptor::parse)
        .filter { it.sizeBytes <= 0L || it.sizeBytes <= availableBytes }
        .filter { it.codec == OfflineVideoCodec.UNKNOWN || it.codec in profile.codecs }
        .filter { it.dynamicRange in profile.dynamicRanges }
        .maxByOrNull { score(it, profile) }

    private fun score(candidate: OfflineStreamDescriptor, profile: OfflineDeviceProfile): Long {
        val resolution = candidate.height?.let { height ->
            if (height <= profile.maxHeight) height.toLong() * 10_000L
            else profile.maxHeight.toLong() * 10_000L - (height - profile.maxHeight) * 20_000L
        } ?: 1L
        val range = when (candidate.dynamicRange) {
            OfflineDynamicRange.DOLBY_VISION -> 900L
            OfflineDynamicRange.HDR10_PLUS -> 800L
            OfflineDynamicRange.HDR10 -> 700L
            OfflineDynamicRange.HLG -> 600L
            OfflineDynamicRange.SDR -> 100L
        }
        val codec = when (candidate.codec) {
            OfflineVideoCodec.AV1 -> 90L
            OfflineVideoCodec.HEVC -> 80L
            OfflineVideoCodec.AVC -> 60L
            OfflineVideoCodec.UNKNOWN -> 0L
        }
        val audio = if (candidate.dolbyAtmos && profile.dolbyAtmos) 50L else 0L
        return resolution + range + codec + audio
    }
}
