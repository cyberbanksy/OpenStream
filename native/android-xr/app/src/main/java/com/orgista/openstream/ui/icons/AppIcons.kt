package com.orgista.openstream.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
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
    val Library = Icons.Filled.List
    val Search = Icons.Filled.Search
    val Settings = Icons.Filled.Settings
    val Back = Icons.Filled.ArrowBack
    val Play = Icons.Filled.PlayArrow
    val Add = Icons.Filled.Add
    val Lock = Icons.Filled.Lock
    val Warning = Icons.Filled.Warning
    val Check = Icons.Filled.CheckCircle
    val Refresh = Icons.Filled.Refresh
    val Close = Icons.Filled.Close

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
