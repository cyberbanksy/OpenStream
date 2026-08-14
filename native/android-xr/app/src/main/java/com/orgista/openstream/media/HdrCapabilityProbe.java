package com.orgista.openstream.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HdrCapabilityProbe {
    private HdrCapabilityProbe() {}

    public static MediaCapabilityReport inspect(Context context) {
        Map<MediaCapabilityReport.DynamicRange, SupportState> display = displaySupport(context);
        DecoderDetails details = decoderDetails();
        Map<MediaCapabilityReport.DynamicRange, SupportState> decoder = decoderSupport(details);
        return new MediaCapabilityReport(
                display,
                decoder,
                details.dolbyVisionProfiles,
                details.dolbyVisionBitstreamProfiles,
                details.hevcMain10,
                details.av1Main10
        );
    }

    private static Map<MediaCapabilityReport.DynamicRange, SupportState> displaySupport(Context context) {
        Map<MediaCapabilityReport.DynamicRange, SupportState> result = baseline();
        Display display;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                display = context.getDisplay();
            } catch (UnsupportedOperationException ignored) {
                display = null;
            }
            if (display == null) {
                DisplayManager manager = context.getSystemService(DisplayManager.class);
                display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            }
        } else {
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            display = manager == null ? null : manager.getDefaultDisplay();
        }
        if (display == null) {
            return result;
        }
        for (int type : display.getHdrCapabilities().getSupportedHdrTypes()) {
            if (type == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                result.put(MediaCapabilityReport.DynamicRange.DOLBY_VISION, SupportState.SUPPORTED);
            } else if (type == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                result.put(MediaCapabilityReport.DynamicRange.HDR10, SupportState.SUPPORTED);
            } else if (type == Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) {
                result.put(MediaCapabilityReport.DynamicRange.HDR10_PLUS, SupportState.SUPPORTED);
            } else if (type == Display.HdrCapabilities.HDR_TYPE_HLG) {
                result.put(MediaCapabilityReport.DynamicRange.HLG, SupportState.SUPPORTED);
            }
        }
        return result;
    }

    private static Map<MediaCapabilityReport.DynamicRange, SupportState> decoderSupport(DecoderDetails details) {
        Map<MediaCapabilityReport.DynamicRange, SupportState> result = baseline();
        result.put(MediaCapabilityReport.DynamicRange.DOLBY_VISION,
                !details.dolbyVisionProfiles.isEmpty() ? SupportState.SUPPORTED : SupportState.UNSUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.HDR10,
                details.hevcMain10 ? SupportState.SUPPORTED : SupportState.UNSUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.HDR10_PLUS,
                details.hevcMain10Plus ? SupportState.SUPPORTED : SupportState.UNSUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.HLG,
                details.hevcMain10 ? SupportState.SUPPORTED : SupportState.UNSUPPORTED);
        return result;
    }

    private static Map<MediaCapabilityReport.DynamicRange, SupportState> baseline() {
        Map<MediaCapabilityReport.DynamicRange, SupportState> result =
                new EnumMap<>(MediaCapabilityReport.DynamicRange.class);
        result.put(MediaCapabilityReport.DynamicRange.SDR, SupportState.SUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.HDR10, SupportState.UNSUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.HDR10_PLUS, SupportState.UNSUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.HLG, SupportState.UNSUPPORTED);
        result.put(MediaCapabilityReport.DynamicRange.DOLBY_VISION, SupportState.UNSUPPORTED);
        return result;
    }

    private static DecoderDetails decoderDetails() {
        Set<String> dolbyProfiles = new LinkedHashSet<>();
        Set<Integer> dolbyBitstreamProfiles = new LinkedHashSet<>();
        boolean hevcMain10 = false;
        boolean hevcMain10Plus = false;
        boolean av1Main10 = false;

        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (info.isEncoder()) {
                continue;
            }
            for (String type : info.getSupportedTypes()) {
                MediaCodecInfo.CodecCapabilities capabilities;
                try {
                    capabilities = info.getCapabilitiesForType(type);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                for (MediaCodecInfo.CodecProfileLevel profileLevel : capabilities.profileLevels) {
                    if (MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(type)) {
                        hevcMain10 |= isHevcMain10(profileLevel.profile);
                        hevcMain10Plus |= isHevcMain10Plus(profileLevel.profile);
                    } else if (MediaFormat.MIMETYPE_VIDEO_AV1.equalsIgnoreCase(type)) {
                        av1Main10 |= isAv1Main10(profileLevel.profile);
                    } else if (MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION.equalsIgnoreCase(type)) {
                        dolbyProfiles.add(dolbyProfileName(profileLevel.profile));
                        DolbyVisionCodecString.bitstreamProfile(profileLevel.profile)
                                .ifPresent(dolbyBitstreamProfiles::add);
                    }
                }
            }
        }
        return new DecoderDetails(
                hevcMain10,
                hevcMain10Plus,
                av1Main10,
                new ArrayList<>(dolbyProfiles),
                new ArrayList<>(dolbyBitstreamProfiles)
        );
    }

    @SuppressLint("InlinedApi")
    private static boolean isHevcMain10(int profile) {
        return profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                || profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                || profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus;
    }

    @SuppressLint("InlinedApi")
    private static boolean isAv1Main10(int profile) {
        return profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                || profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
                || profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus;
    }

    @SuppressLint("InlinedApi")
    private static boolean isHevcMain10Plus(int profile) {
        return profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus;
    }

    private static String dolbyProfileName(int profile) {
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPen) return "DvavPen";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavPer) return "DvavPer";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe) return "DvavSe";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDen) return "DvheDen";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDer) return "DvheDer";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb) return "DvheDtb";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDth) return "DvheDth";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr) return "DvheDtr";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt) return "DvheSt";
        if (profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn) return "DvheStn";
        if (Build.VERSION.SDK_INT >= 35
                && profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110) {
            return "Dvav110";
        }
        return "platform-profile-" + profile;
    }

    private record DecoderDetails(
            boolean hevcMain10,
            boolean hevcMain10Plus,
            boolean av1Main10,
            List<String> dolbyVisionProfiles,
            List<Integer> dolbyVisionBitstreamProfiles
    ) {}
}
