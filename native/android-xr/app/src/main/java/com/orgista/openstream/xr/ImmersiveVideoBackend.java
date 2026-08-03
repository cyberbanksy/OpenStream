package com.orgista.openstream.xr;

import android.app.Activity;

public interface ImmersiveVideoBackend {
    boolean isAvailable(Activity activity);

    LaunchResult launch(Activity activity, ImmersiveMediaDescriptor media);

    record LaunchResult(boolean launched, String message) {}
}
