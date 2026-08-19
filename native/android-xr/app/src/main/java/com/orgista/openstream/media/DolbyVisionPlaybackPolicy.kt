package com.orgista.openstream.media

import androidx.media3.common.MimeTypes

enum class DolbyVisionCompatibility {
    NOT_DOLBY_VISION,
    SUPPORTED,
    UNSUPPORTED,
    UNPROFILED,
}

/** Decides whether an extracted Dolby Vision track matches the profiles reported by the decoder. */
object DolbyVisionPlaybackPolicy {
    fun compatibility(
        sampleMimeType: String?,
        codecs: String?,
        supportedProfiles: Set<Int>,
    ): DolbyVisionCompatibility {
        val descriptor = DolbyVisionCodecString.parse(codecs).orElse(null)
        val isDolbyVision = sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION || descriptor != null
        if (!isDolbyVision) return DolbyVisionCompatibility.NOT_DOLBY_VISION
        if (descriptor == null) return DolbyVisionCompatibility.UNPROFILED
        if (supportedProfiles.isEmpty() || descriptor.profile() in supportedProfiles) {
            return DolbyVisionCompatibility.SUPPORTED
        }
        return DolbyVisionCompatibility.UNSUPPORTED
    }
}
