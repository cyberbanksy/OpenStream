package com.cyberbanksy.openstream.xr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCoreVideoPolicyTest {
    @Test
    fun mapsStandardSpatialSurfaces() {
        assertEquals(SceneSurfaceShape.QUAD, plan(Projection.FLAT).shape)
        assertEquals(SceneSurfaceShape.HEMISPHERE, plan(Projection.EQUIRECTANGULAR_180).shape)
        assertEquals(SceneSurfaceShape.SPHERE, plan(Projection.EQUIRECTANGULAR_360).shape)
        assertTrue(plan(Projection.EQUIRECTANGULAR_360).directlyRenderable)
    }

    @Test
    fun routesProjectionSpecificUvFormatsToNativeMeshRenderer() {
        assertFalse(plan(Projection.FISHEYE_180).directlyRenderable)
        assertFalse(plan(Projection.CUBEMAP_360).directlyRenderable)
        assertEquals(SceneSurfaceShape.CUSTOM_MESH, plan(Projection.CUBEMAP_360).shape)
    }

    @Test
    fun classifiesPackedAndMultiviewLayouts() {
        assertEquals(StereoLayout.SIDE_BY_SIDE, ImmersiveMediaClassifier.stereo("movie sbs 3d"))
        assertEquals(StereoLayout.TOP_BOTTOM, ImmersiveMediaClassifier.stereo("movie over-under"))
        assertEquals(
            StereoLayout.MULTIVIEW_LEFT_PRIMARY,
            ImmersiveMediaClassifier.stereo("spatial mv-hevc"),
        )
    }

    private fun plan(projection: Projection): SceneCoreVideoPlan = SceneCoreVideoPolicy.plan(
        ImmersiveMedia(
            uri = null,
            projection = projection,
            stereoLayout = StereoLayout.MONO,
            dynamicRange = DynamicRange.SDR,
        )
    )
}
