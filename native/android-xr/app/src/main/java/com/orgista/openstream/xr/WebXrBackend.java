package com.orgista.openstream.xr;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import com.orgista.openstream.BuildConfig;

public final class WebXrBackend implements ImmersiveVideoBackend {
    private final XrRuntimeReport runtime;

    public WebXrBackend(XrRuntimeReport runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean isAvailable(Activity activity) {
        // A blank WEB_XR_URL means this build has no configured viewer (release
        // builds ship empty until a real URL is set). Report unavailable so the
        // caller keeps the flat player instead of opening a dead loopback.
        return runtime.immersiveRuntimeAvailable() && hasViewerUrl();
    }

    private static boolean hasViewerUrl() {
        String url = BuildConfig.WEB_XR_URL;
        return url != null && !url.trim().isEmpty();
    }

    @Override
    public LaunchResult launch(Activity activity, ImmersiveMediaDescriptor media) {
        if (!hasViewerUrl()) {
            return new LaunchResult(false, "The immersive viewer is not configured for this build");
        }
        Uri viewer = Uri.parse(BuildConfig.WEB_XR_URL).buildUpon()
                .appendQueryParameter("src", media.uri().toString())
                .appendQueryParameter("projection", projectionValue(media.projection()))
                .appendQueryParameter("stereo", stereoValue(media.stereoMode()))
                .appendQueryParameter("range", media.dynamicRange())
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, viewer);
        try {
            activity.startActivity(intent);
            return new LaunchResult(true, "Opened the standards-based WebXR viewer");
        } catch (ActivityNotFoundException error) {
            return new LaunchResult(false, "No WebXR-capable browser is installed");
        }
    }

    private String projectionValue(ImmersiveMediaDescriptor.Projection projection) {
        return switch (projection) {
            case EQUIRECTANGULAR_180, FISHEYE_180 -> "180";
            case EQUIRECTANGULAR_360 -> "360";
            case CUBEMAP_360 -> "cube";
            case WIDE_FIELD_OF_VIEW -> "wide";
            case FLAT -> "flat";
        };
    }

    private String stereoValue(ImmersiveMediaDescriptor.StereoMode stereoMode) {
        return switch (stereoMode) {
            case MONO -> "mono";
            case SIDE_BY_SIDE -> "sbs";
            case TOP_BOTTOM -> "tb";
            case MULTIVIEW_HEVC -> "mv-hevc";
        };
    }
}
