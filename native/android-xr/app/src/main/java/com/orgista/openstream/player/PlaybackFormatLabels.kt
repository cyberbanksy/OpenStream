package com.orgista.openstream.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

data class PlaybackTrackSignal(
    val videoCodecs: String? = null,
    val colorTransfer: Int? = null,
    val hasHdrStaticInfo: Boolean = false,
    val audioMimeType: String? = null,
    val audioCodecs: String? = null,
    val sourceHint: String? = null,
)

@OptIn(UnstableApi::class)
object PlaybackFormatLabels {
    fun forSignal(signal: PlaybackTrackSignal): List<String> = buildList {
        val videoCodecs = signal.videoCodecs.orEmpty().lowercase()
        when {
            listOf("dvhe", "dvh1", "dolby-vision").any(videoCodecs::contains) -> add("Dolby Vision")
            "hdr10+" in videoCodecs || "hdr10plus" in videoCodecs -> add("HDR10+")
            signal.colorTransfer == C.COLOR_TRANSFER_ST2084 ->
                add(if (signal.hasHdrStaticInfo) "HDR10" else "PQ HDR")
            signal.colorTransfer == C.COLOR_TRANSFER_HLG -> add("HLG")
        }

        val audioMime = signal.audioMimeType.orEmpty().lowercase()
        val audioCodecs = signal.audioCodecs.orEmpty().lowercase()
        val sourceHint = signal.sourceHint.orEmpty().lowercase()
        val atmosCapableCarrier = audioMime == MimeTypes.AUDIO_E_AC3 ||
            audioMime == MimeTypes.AUDIO_E_AC3_JOC ||
            audioMime == MimeTypes.AUDIO_TRUEHD
        when {
            audioMime == MimeTypes.AUDIO_E_AC3_JOC || "joc" in audioCodecs -> add("Dolby Atmos")
            atmosCapableCarrier && "atmos" in sourceHint -> add("Dolby Atmos")
            audioMime == MimeTypes.AUDIO_TRUEHD -> add("Dolby TrueHD")
        }
    }.distinct()
}
