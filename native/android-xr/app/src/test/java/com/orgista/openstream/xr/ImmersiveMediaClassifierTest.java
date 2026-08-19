package com.orgista.openstream.xr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ImmersiveMediaClassifierTest {
    @Test
    public void recognizesCommonProjectionConventions() {
        assertEquals(
                ImmersiveMediaDescriptor.Projection.EQUIRECTANGULAR_360,
                ImmersiveMediaClassifier.projection("travel_360_equirect.mp4")
        );
        assertEquals(
                ImmersiveMediaDescriptor.Projection.FISHEYE_180,
                ImmersiveMediaClassifier.projection("concert-vr180-fisheye.mkv")
        );
        assertEquals(
                ImmersiveMediaDescriptor.Projection.CUBEMAP_360,
                ImmersiveMediaClassifier.projection("documentary equi-angular-cubemap")
        );
        assertEquals(
                ImmersiveMediaDescriptor.Projection.FLAT,
                ImmersiveMediaClassifier.projection("ordinary feature film")
        );
    }

    @Test
    public void recognizesStereoLayoutsAndMvHevc() {
        assertEquals(
                ImmersiveMediaDescriptor.StereoMode.SIDE_BY_SIDE,
                ImmersiveMediaClassifier.stereo("VR180 SBS")
        );
        assertEquals(
                ImmersiveMediaDescriptor.StereoMode.TOP_BOTTOM,
                ImmersiveMediaClassifier.stereo("360 over-under")
        );
        assertEquals(
                ImmersiveMediaDescriptor.StereoMode.MULTIVIEW_HEVC,
                ImmersiveMediaClassifier.stereo("Apple spatial MV-HEVC")
        );
        assertEquals(
                ImmersiveMediaDescriptor.StereoMode.MONO,
                ImmersiveMediaClassifier.stereo("mono 360")
        );
    }
}
