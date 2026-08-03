package com.orgista.openstream.xr;

import android.net.Uri;

public record ImmersiveMediaDescriptor(
        Uri uri,
        Projection projection,
        StereoMode stereoMode,
        String dynamicRange
) {
    public enum Projection {
        FLAT,
        EQUIRECTANGULAR_180,
        EQUIRECTANGULAR_360,
        FISHEYE_180,
        WIDE_FIELD_OF_VIEW
    }

    public enum StereoMode {
        MONO,
        SIDE_BY_SIDE,
        TOP_BOTTOM,
        MULTIVIEW_HEVC
    }
}
