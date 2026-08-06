package com.cyberbanksy.openstream.xr

enum class SceneSurfaceShape {
    QUAD,
    HEMISPHERE,
    SPHERE,
    CUSTOM_MESH,
}

data class SceneCoreVideoPlan(
    val shape: SceneSurfaceShape,
    val widthMeters: Float,
    val heightMeters: Float,
    val radiusMeters: Float,
    val stereoLayout: StereoLayout,
    val dynamicRange: DynamicRange,
    val directlyRenderable: Boolean,
    val unsupportedReason: String? = null,
)

object SceneCoreVideoPolicy {
    fun plan(media: ImmersiveMedia): SceneCoreVideoPlan {
        val shape = when (media.projection) {
            Projection.FLAT -> SceneSurfaceShape.QUAD
            Projection.WIDE_FIELD_OF_VIEW -> SceneSurfaceShape.QUAD
            Projection.EQUIRECTANGULAR_180 -> SceneSurfaceShape.HEMISPHERE
            Projection.EQUIRECTANGULAR_360 -> SceneSurfaceShape.SPHERE
            Projection.FISHEYE_180,
            Projection.CUBEMAP_360,
            -> SceneSurfaceShape.CUSTOM_MESH
        }
        val direct = shape != SceneSurfaceShape.CUSTOM_MESH
        return SceneCoreVideoPlan(
            shape = shape,
            widthMeters = if (media.projection == Projection.WIDE_FIELD_OF_VIEW) 3.2f else 2.0f,
            heightMeters = if (media.projection == Projection.WIDE_FIELD_OF_VIEW) 1.2f else 1.125f,
            radiusMeters = 8.0f,
            stereoLayout = media.stereoLayout,
            dynamicRange = media.dynamicRange,
            directlyRenderable = direct,
            unsupportedReason = if (direct) null else
                "This projection needs the native custom-mesh renderer; use the OpenStream Quest build.",
        )
    }
}
