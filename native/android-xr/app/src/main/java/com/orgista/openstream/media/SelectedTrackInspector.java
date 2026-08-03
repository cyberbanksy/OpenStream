package com.orgista.openstream.media;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;

import java.util.Optional;

@UnstableApi
public final class SelectedTrackInspector {
    public record TrackResult(
            String codec,
            String dynamicRange,
            String dolbyVisionProfile,
            int width,
            int height,
            float frameRate
    ) {}

    private SelectedTrackInspector() {}

    public static TrackResult inspect(Format format) {
        String codec = format.codecs == null ? "unknown" : format.codecs;
        Optional<DolbyVisionCodecString.Descriptor> dolby = DolbyVisionCodecString.parse(codec);
        String range = dynamicRange(format.colorInfo, dolby.isPresent());
        String profile = dolby.map(DolbyVisionCodecString.Descriptor::label).orElse("not signaled");
        return new TrackResult(codec, range, profile, format.width, format.height, format.frameRate);
    }

    private static String dynamicRange(ColorInfo colorInfo, boolean dolbyVision) {
        if (dolbyVision) {
            return "Dolby Vision";
        }
        if (colorInfo == null) {
            return "runtime check";
        }
        if (colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) {
            return colorInfo.hdrStaticInfo == null ? "PQ HDR" : "HDR10";
        }
        if (colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) {
            return "HLG";
        }
        return "SDR";
    }
}
