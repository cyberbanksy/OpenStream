package com.orgista.openstream.xr;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;

public final class XrRuntimeProbe {
    public static final String FEATURE_ANDROID_XR_SPATIAL = "android.software.xr.api.spatial";
    public static final String FEATURE_OPEN_XR = "android.software.xr.api.openxr";
    public static final String FEATURE_XR_CONTROLLER = "android.hardware.xr.input.controller";

    private XrRuntimeProbe() {}

    public static XrRuntimeReport inspect(Context context) {
        PackageManager manager = context.getPackageManager();
        boolean spatial = manager.hasSystemFeature(FEATURE_ANDROID_XR_SPATIAL);
        boolean openXr = manager.hasSystemFeature(FEATURE_OPEN_XR, 0x00010001);
        boolean controller = manager.hasSystemFeature(FEATURE_XR_CONTROLLER);

        String manufacturer = normalized(Build.MANUFACTURER);
        String hardware = normalized(Build.HARDWARE + " " + Build.DEVICE + " " + Build.PRODUCT);
        boolean meta = manufacturer.contains("meta")
                || manufacturer.contains("oculus")
                || hardware.contains("quest");
        boolean samsung = manufacturer.contains("samsung") && (spatial || openXr);

        String label;
        if (meta) {
            label = "Meta Quest OpenXR runtime";
        } else if (samsung) {
            label = "Samsung Android XR runtime";
        } else if (openXr) {
            label = "Android XR OpenXR 1.1 runtime";
        } else if (spatial) {
            label = "Android XR spatial runtime";
        } else {
            label = "2D Android compatibility mode";
        }
        return new XrRuntimeReport(spatial, openXr, controller, meta, samsung, label);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
