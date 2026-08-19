package com.cyberbanksy.openstream.xr

import android.net.Uri
import java.util.Locale

enum class Projection {
    FLAT,
    WIDE_FIELD_OF_VIEW,
    EQUIRECTANGULAR_180,
    EQUIRECTANGULAR_360,
    FISHEYE_180,
    CUBEMAP_360,
}

enum class StereoLayout {
    MONO,
    SIDE_BY_SIDE,
    TOP_BOTTOM,
    MULTIVIEW_LEFT_PRIMARY,
}

enum class DynamicRange {
    SDR,
    HDR10,
    HDR10_PLUS,
    HLG,
    DOLBY_VISION,
}

data class ImmersiveMedia(
    val uri: Uri?,
    val projection: Projection,
    val stereoLayout: StereoLayout,
    val dynamicRange: DynamicRange,
)

object ImmersiveMediaClassifier {
    fun classify(
        uri: Uri,
        title: String?,
        projectionMetadata: String?,
        stereoMetadata: String?,
        dynamicRangeMetadata: String?,
    ): ImmersiveMedia {
        val projectionSignal = normalize("${title.orEmpty()} ${projectionMetadata.orEmpty()}")
        val stereoSignal = normalize("${title.orEmpty()} ${stereoMetadata.orEmpty()}")
        return ImmersiveMedia(
            uri = uri,
            projection = projection(projectionSignal),
            stereoLayout = stereo(stereoSignal),
            dynamicRange = dynamicRange(dynamicRangeMetadata),
        )
    }

    fun projection(signal: String): Projection {
        val value = normalize(signal)
        return when {
            value.containsAny("cubemap", "cube map", "eac", "equi angular cubemap") ->
                Projection.CUBEMAP_360
            value.containsAny("fisheye", "fish eye", "vr180") -> Projection.FISHEYE_180
            value.containsAny("180", "hemisphere", "half sphere") ->
                Projection.EQUIRECTANGULAR_180
            value.containsAny("360", "equirect", "sphere", "spherical") ->
                Projection.EQUIRECTANGULAR_360
            value.containsAny("wide fov", "wide field", "panorama", "panoramic") ->
                Projection.WIDE_FIELD_OF_VIEW
            else -> Projection.FLAT
        }
    }

    fun stereo(signal: String): StereoLayout {
        val value = normalize(signal)
        return when {
            value.containsAny("mv-hevc", "mvhevc", "multiview hevc", "spatial hevc") ->
                StereoLayout.MULTIVIEW_LEFT_PRIMARY
            value.containsAny("top bottom", "top-bottom", "over under", "over-under", "tb3d", "ou3d") ->
                StereoLayout.TOP_BOTTOM
            value.containsAny("side by side", "side-by-side", "sbs", "lr3d", "rl3d") ->
                StereoLayout.SIDE_BY_SIDE
            else -> StereoLayout.MONO
        }
    }

    fun dynamicRange(signal: String?): DynamicRange {
        val value = normalize(signal.orEmpty())
        return when {
            value.containsAny("dolby vision", "dolbyvision", "dovi", "dv") -> DynamicRange.DOLBY_VISION
            value.containsAny("hdr10+", "hdr10 plus", "hdr10plus") -> DynamicRange.HDR10_PLUS
            value.contains("hlg") -> DynamicRange.HLG
            value.containsAny("hdr10", "hdr") -> DynamicRange.HDR10
            else -> DynamicRange.SDR
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean = needles.any(::contains)

    private fun normalize(value: String): String =
        value.lowercase(Locale.US).replace('_', ' ').replace('.', ' ').trim()
}
