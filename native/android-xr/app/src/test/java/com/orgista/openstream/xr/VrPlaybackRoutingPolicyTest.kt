package com.orgista.openstream.xr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VrPlaybackRoutingPolicyTest {
    private fun media(
        projection: ImmersiveMediaDescriptor.Projection = ImmersiveMediaDescriptor.Projection.FLAT,
        stereo: ImmersiveMediaDescriptor.StereoMode = ImmersiveMediaDescriptor.StereoMode.MONO,
    ) = ImmersiveMediaDescriptor(null, projection, stereo, "")

    @Test
    fun `flat media remains in the regular player on a headset`() {
        assertFalse(VrPlaybackRoutingPolicy.shouldUseImmersivePlayer(true, media()))
    }

    @Test
    fun `immersive media enters the headset player when a runtime is available`() {
        assertTrue(
            VrPlaybackRoutingPolicy.shouldUseImmersivePlayer(
                true,
                media(ImmersiveMediaDescriptor.Projection.EQUIRECTANGULAR_180),
            ),
        )
        assertTrue(
            VrPlaybackRoutingPolicy.shouldUseImmersivePlayer(
                true,
                media(stereo = ImmersiveMediaDescriptor.StereoMode.SIDE_BY_SIDE),
            ),
        )
    }

    @Test
    fun `immersive metadata does not leave the native player on a regular tablet`() {
        assertFalse(
            VrPlaybackRoutingPolicy.shouldUseImmersivePlayer(
                false,
                media(ImmersiveMediaDescriptor.Projection.EQUIRECTANGULAR_360),
            ),
        )
    }
}
