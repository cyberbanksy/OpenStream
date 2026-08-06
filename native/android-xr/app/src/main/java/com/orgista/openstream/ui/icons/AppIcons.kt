package com.orgista.openstream.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon set built from the bundled core icons plus two small custom glyphs, so
 * the app avoids the multi-megabyte extended icon dependency.
 */
object AppIcons {
    val Home = Icons.Filled.Home
    val Library = Icons.AutoMirrored.Filled.List
    val Search = Icons.Filled.Search
    val Settings = Icons.Filled.Settings
    val Back = Icons.AutoMirrored.Filled.ArrowBack
    val Play = Icons.Filled.PlayArrow
    val Add = Icons.Filled.Add
    val Lock = Icons.Filled.Lock
    val More = Icons.Filled.MoreVert
    val Warning = Icons.Filled.Warning
    val Check = Icons.Filled.CheckCircle
    val Refresh = Icons.Filled.Refresh
    val Close = Icons.Filled.Close

    /** Compact Wi-Fi glyph for the shared OpenPanel-style device header. */
    val Wifi: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wifi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero) {
                moveTo(1f, 9f)
                lineTo(3f, 11f)
                curveTo(7.97f, 6.03f, 16.03f, 6.03f, 21f, 11f)
                lineTo(23f, 9f)
                curveTo(16.93f, 2.93f, 7.07f, 2.93f, 1f, 9f)
                close()
                moveTo(5f, 13f)
                lineTo(7f, 15f)
                curveTo(9.76f, 12.24f, 14.24f, 12.24f, 17f, 15f)
                lineTo(19f, 13f)
                curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f)
                close()
                moveTo(9f, 17f)
                lineTo(12f, 20f)
                lineTo(15f, 17f)
                curveTo(13.35f, 15.34f, 10.66f, 15.34f, 9f, 17f)
                close()
            }
        }.build()
    }

    /** Hard-drive glyph for NAS/storage surfaces. */
    val Storage: ImageVector by lazy {
        ImageVector.Builder(
            name = "Storage",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero) {
                moveTo(4f, 6f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 8f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 18f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 16f)
                verticalLineTo(8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 6f)
                close()
                moveTo(17f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 17f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17f, 13.5f)
                close()
            }
        }.build()
    }

    /** Folder glyph for the NAS browser. */
    val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.NonZero) {
                moveTo(4f, 4f)
                horizontalLineTo(10f)
                lineTo(12f, 6f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 8f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 20f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 18f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 4f)
                close()
            }
        }.build()
    }
}
