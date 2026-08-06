package com.orgista.openstream.offline

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import android.view.WindowManager
import com.orgista.openstream.media.HdrCapabilityProbe
import com.orgista.openstream.media.MediaCapabilityReport
import com.orgista.openstream.media.SupportState

object OfflineDeviceCapabilities {
    fun inspect(context: Context): OfflineDeviceProfile {
        val mode = runCatching {
            val display = if (Build.VERSION.SDK_INT >= 30) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }
            display?.mode
        }.getOrNull()
        val physicalWidth = mode?.physicalWidth ?: context.resources.displayMetrics.widthPixels
        val physicalHeight = mode?.physicalHeight ?: context.resources.displayMetrics.heightPixels
        val maxWidth = maxOf(physicalWidth, physicalHeight)
        val maxHeight = minOf(physicalWidth, physicalHeight)

        val codecs = runCatching {
            buildSet {
                add(OfflineVideoCodec.AVC)
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .asSequence()
                    .filterNot { it.isEncoder }
                    .filter { Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated }
                    .flatMap { it.supportedTypes.asSequence() }
                    .forEach { type ->
                        when (type.lowercase()) {
                            "video/hevc" -> add(OfflineVideoCodec.HEVC)
                            "video/av01" -> add(OfflineVideoCodec.AV1)
                        }
                    }
            }
        }.getOrDefault(setOf(OfflineVideoCodec.AVC))

        val ranges = buildSet {
            add(OfflineDynamicRange.SDR)
            val report = runCatching { HdrCapabilityProbe.inspect(context) }.getOrNull() ?: return@buildSet
            fun addIfPlayable(
                reportRange: MediaCapabilityReport.DynamicRange,
                offlineRange: OfflineDynamicRange,
            ) {
                if (report.playableState(reportRange) != SupportState.UNSUPPORTED) add(offlineRange)
            }
            addIfPlayable(MediaCapabilityReport.DynamicRange.HDR10, OfflineDynamicRange.HDR10)
            addIfPlayable(MediaCapabilityReport.DynamicRange.HDR10_PLUS, OfflineDynamicRange.HDR10_PLUS)
            addIfPlayable(MediaCapabilityReport.DynamicRange.HLG, OfflineDynamicRange.HLG)
            addIfPlayable(MediaCapabilityReport.DynamicRange.DOLBY_VISION, OfflineDynamicRange.DOLBY_VISION)
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val atmos = if (Build.VERSION.SDK_INT >= 23) {
            runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { output ->
                    output.encodings.any { encoding ->
                        encoding == AudioFormat.ENCODING_E_AC3_JOC || encoding == AudioFormat.ENCODING_DOLBY_TRUEHD
                    }
                }
            }.getOrDefault(false)
        } else {
            false
        }
        return OfflineDeviceProfile(maxWidth, maxHeight, codecs, ranges, atmos)
    }
}
