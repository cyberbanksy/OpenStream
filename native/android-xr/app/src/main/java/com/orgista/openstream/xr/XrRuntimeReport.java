package com.orgista.openstream.xr;

public record XrRuntimeReport(
        boolean androidSpatialApi,
        boolean openXr11,
        boolean sixDofControllers,
        boolean metaQuest,
        boolean samsungXr,
        String runtimeLabel
) {
    public boolean immersiveRuntimeAvailable() {
        return openXr11 || metaQuest;
    }
}
