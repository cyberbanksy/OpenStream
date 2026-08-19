package com.orgista.openstream.media;

import android.media.MediaCodecInfo;
import android.os.Build;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

public final class DolbyVisionCodecString {
    public record Descriptor(String sampleEntry, int profile, int level) {
        public String label() {
            return String.format(Locale.US, "Dolby Vision P%d L%02d (%s)", profile, level, sampleEntry);
        }
    }

    private DolbyVisionCodecString() {}

    public static Optional<Descriptor> parse(String codecs) {
        if (codecs == null || codecs.isBlank()) {
            return Optional.empty();
        }
        String[] candidates = codecs.toLowerCase(Locale.US).split(",");
        for (String candidate : candidates) {
            String[] parts = candidate.trim().split("\\.");
            if (parts.length < 3 || !isDolbyVisionSampleEntry(parts[0])) {
                continue;
            }
            try {
                int profile = Integer.parseInt(parts[1]);
                int level = Integer.parseInt(parts[2]);
                if (profile < 0 || profile > 31 || level < 0 || level > 63) {
                    return Optional.empty();
                }
                return Optional.of(new Descriptor(parts[0], profile, level));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Maps Android decoder profile constants to the profile number carried by dvhe/dvh1/dvav. */
    public static OptionalInt bitstreamProfile(int platformProfile) {
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPer) {
            return OptionalInt.of(0);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPen) {
            return OptionalInt.of(1);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDer) {
            return OptionalInt.of(2);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDen) {
            return OptionalInt.of(3);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr) {
            return OptionalInt.of(4);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn) {
            return OptionalInt.of(5);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDth) {
            return OptionalInt.of(6);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb) {
            return OptionalInt.of(7);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt) {
            return OptionalInt.of(8);
        }
        if (platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe) {
            return OptionalInt.of(9);
        }
        if (Build.VERSION.SDK_INT >= 30
                && platformProfile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110) {
            return OptionalInt.of(10);
        }
        return OptionalInt.empty();
    }

    private static boolean isDolbyVisionSampleEntry(String sampleEntry) {
        return "dvh1".equals(sampleEntry)
                || "dvhe".equals(sampleEntry)
                || "dvav".equals(sampleEntry)
                || "dva1".equals(sampleEntry);
    }
}
