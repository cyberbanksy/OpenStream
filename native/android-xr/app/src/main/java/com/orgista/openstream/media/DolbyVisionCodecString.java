package com.orgista.openstream.media;

import java.util.Locale;
import java.util.Optional;

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

    private static boolean isDolbyVisionSampleEntry(String sampleEntry) {
        return "dvh1".equals(sampleEntry)
                || "dvhe".equals(sampleEntry)
                || "dvav".equals(sampleEntry)
                || "dva1".equals(sampleEntry);
    }
}
