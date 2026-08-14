package com.orgista.openstream.xr

object VrPlaybackRoutingPolicy {
    @JvmStatic
    fun shouldUseImmersivePlayer(
        immersiveRuntimeAvailable: Boolean,
        media: ImmersiveMediaDescriptor,
    ): Boolean = immersiveRuntimeAvailable && (
        media.projection() != ImmersiveMediaDescriptor.Projection.FLAT ||
            media.stereoMode() != ImmersiveMediaDescriptor.StereoMode.MONO
        )
}
