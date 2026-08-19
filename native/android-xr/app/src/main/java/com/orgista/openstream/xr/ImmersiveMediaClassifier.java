package com.orgista.openstream.xr;

import android.net.Uri;

import java.util.Locale;

/**
 * Normalizes the projection conventions used by VR cameras, add-ons, and
 * filenames into the small, explicit contract understood by every headset
 * backend. The parser is intentionally deterministic and never opens a URI.
 */
public final class ImmersiveMediaClassifier {
    private ImmersiveMediaClassifier() {}

    public static ImmersiveMediaDescriptor classify(
            Uri uri,
            String name,
            String projectionMetadata,
            String stereoMetadata,
            String dynamicRange
    ) {
        String projectionSignal = normalize(name + " " + projectionMetadata);
        String stereoSignal = normalize(name + " " + stereoMetadata);
        return new ImmersiveMediaDescriptor(
                uri,
                projection(projectionSignal),
                stereo(stereoSignal),
                dynamicRange == null ? "" : dynamicRange
        );
    }

    static ImmersiveMediaDescriptor.Projection projection(String signal) {
        String value = normalize(signal);
        if (containsAny(value, "cubemap", "cube map", "eac", "equ angular cubemap")) {
            return ImmersiveMediaDescriptor.Projection.CUBEMAP_360;
        }
        if (containsAny(value, "fisheye", "fish eye", "vr180")) {
            return ImmersiveMediaDescriptor.Projection.FISHEYE_180;
        }
        if (containsAny(value, "180", "hemisphere", "half sphere")) {
            return ImmersiveMediaDescriptor.Projection.EQUIRECTANGULAR_180;
        }
        if (containsAny(value, "360", "equirect", "sphere", "spherical")) {
            return ImmersiveMediaDescriptor.Projection.EQUIRECTANGULAR_360;
        }
        if (containsAny(value, "wide fov", "wide field", "panorama", "panoramic")) {
            return ImmersiveMediaDescriptor.Projection.WIDE_FIELD_OF_VIEW;
        }
        return ImmersiveMediaDescriptor.Projection.FLAT;
    }

    static ImmersiveMediaDescriptor.StereoMode stereo(String signal) {
        String value = normalize(signal);
        if (containsAny(value, "mv-hevc", "mvhevc", "multiview hevc", "spatial hevc")) {
            return ImmersiveMediaDescriptor.StereoMode.MULTIVIEW_HEVC;
        }
        if (containsAny(value, "top bottom", "top-bottom", "over under", "over-under", "tb3d", "ou3d")) {
            return ImmersiveMediaDescriptor.StereoMode.TOP_BOTTOM;
        }
        if (containsAny(value, "side by side", "side-by-side", "sbs", "lr3d", "rl3d")) {
            return ImmersiveMediaDescriptor.StereoMode.SIDE_BY_SIDE;
        }
        return ImmersiveMediaDescriptor.StereoMode.MONO;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.US).replace('_', ' ').replace('.', ' ').trim();
    }
}
