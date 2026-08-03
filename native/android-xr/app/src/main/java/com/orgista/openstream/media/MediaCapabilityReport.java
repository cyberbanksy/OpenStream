package com.orgista.openstream.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MediaCapabilityReport {
    public enum DynamicRange {
        SDR,
        HDR10,
        HDR10_PLUS,
        HLG,
        DOLBY_VISION
    }

    private final Map<DynamicRange, SupportState> displaySupport;
    private final Map<DynamicRange, SupportState> decoderSupport;
    private final List<String> dolbyVisionCodecProfiles;
    private final boolean hevcMain10Decoder;
    private final boolean av1Main10Decoder;

    public MediaCapabilityReport(
            Map<DynamicRange, SupportState> displaySupport,
            Map<DynamicRange, SupportState> decoderSupport,
            List<String> dolbyVisionCodecProfiles,
            boolean hevcMain10Decoder,
            boolean av1Main10Decoder
    ) {
        this.displaySupport = Collections.unmodifiableMap(new EnumMap<>(displaySupport));
        this.decoderSupport = Collections.unmodifiableMap(new EnumMap<>(decoderSupport));
        this.dolbyVisionCodecProfiles = Collections.unmodifiableList(
                new ArrayList<>(dolbyVisionCodecProfiles)
        );
        this.hevcMain10Decoder = hevcMain10Decoder;
        this.av1Main10Decoder = av1Main10Decoder;
    }

    public Map<DynamicRange, SupportState> displaySupport() {
        return displaySupport;
    }

    public Map<DynamicRange, SupportState> decoderSupport() {
        return decoderSupport;
    }

    public List<String> dolbyVisionCodecProfiles() {
        return dolbyVisionCodecProfiles;
    }

    public boolean hasHevcMain10Decoder() {
        return hevcMain10Decoder;
    }

    public boolean hasAv1Main10Decoder() {
        return av1Main10Decoder;
    }

    public SupportState playableState(DynamicRange range) {
        SupportState display = displaySupport.getOrDefault(range, SupportState.RUNTIME_CHECK);
        SupportState decoder = decoderSupport.getOrDefault(range, SupportState.RUNTIME_CHECK);
        if (display == SupportState.UNSUPPORTED || decoder == SupportState.UNSUPPORTED) {
            return SupportState.UNSUPPORTED;
        }
        if (display == SupportState.SUPPORTED && decoder == SupportState.SUPPORTED) {
            return SupportState.SUPPORTED;
        }
        return SupportState.RUNTIME_CHECK;
    }
}
