package com.orgista.openstream.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFormatLabelsTest {
    @Test
    fun reportsDolbyVisionAndAtmosFromSelectedTracks() {
        val labels = PlaybackFormatLabels.forSignal(
            PlaybackTrackSignal(
                videoCodecs = "dvhe.08.07",
                colorTransfer = C.COLOR_TRANSFER_ST2084,
                hasHdrStaticInfo = true,
                audioMimeType = MimeTypes.AUDIO_E_AC3_JOC,
                audioCodecs = "ec-3",
            ),
        )

        assertEquals(listOf("Dolby Vision", "Dolby Atmos"), labels)
    }

    @Test
    fun reportsHdr10HlgAndTrueHdWithoutOverclaimingAtmos() {
        assertEquals(
            listOf("HDR10", "Dolby TrueHD"),
            PlaybackFormatLabels.forSignal(
                PlaybackTrackSignal(
                    videoCodecs = "hvc1.2.4.L150",
                    colorTransfer = C.COLOR_TRANSFER_ST2084,
                    hasHdrStaticInfo = true,
                    audioMimeType = MimeTypes.AUDIO_TRUEHD,
                ),
            ),
        )
        assertEquals(
            listOf("HLG"),
            PlaybackFormatLabels.forSignal(
                PlaybackTrackSignal(colorTransfer = C.COLOR_TRANSFER_HLG),
            ),
        )
    }

    @Test
    fun suppressesIndicatorForOrdinarySdrStereoPlayback() {
        assertTrueEmpty(
            PlaybackFormatLabels.forSignal(
                PlaybackTrackSignal(
                    videoCodecs = "avc1.640028",
                    colorTransfer = C.COLOR_TRANSFER_SDR,
                    audioMimeType = MimeTypes.AUDIO_AAC,
                ),
            ),
        )
    }

    @Test
    fun acceptsAtmosSourceMetadataOnlyForAnAtmosCapableSelectedCarrier() {
        assertEquals(
            listOf("Dolby Atmos"),
            PlaybackFormatLabels.forSignal(
                PlaybackTrackSignal(
                    audioMimeType = MimeTypes.AUDIO_E_AC3,
                    sourceHint = "2160p DV HDR10+ Atmos",
                ),
            ),
        )
        assertTrueEmpty(
            PlaybackFormatLabels.forSignal(
                PlaybackTrackSignal(
                    audioMimeType = MimeTypes.AUDIO_AAC,
                    sourceHint = "unverified Atmos label",
                ),
            ),
        )
    }

    private fun assertTrueEmpty(values: List<String>) = assertEquals(emptyList<String>(), values)
}
