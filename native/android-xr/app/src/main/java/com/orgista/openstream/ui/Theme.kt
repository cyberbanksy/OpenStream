package com.orgista.openstream.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object OpenStreamColors {
    val Canvas = Color(0xFF05080B)
    val CanvasDeep = Color(0xFF020406)
    val Surface = Color(0xFF10161A)
    val SurfaceRaised = Color(0xFF171F24)
    val Text = Color(0xFFF4F7F8)
    val TextSoft = Color(0xFFC2CBD0)
    val Muted = Color(0xFF89959D)
    val Cyan = Color(0xFF48DCE7)
    val Amber = Color(0xFFF5B84B)
    val Coral = Color(0xFFFF6D54)
    val Green = Color(0xFF72DF8B)
    val Danger = Color(0xFFFF7B73)
    val Border = Color(0x24E6F0F5)
}

private val scheme = darkColorScheme(
    primary = OpenStreamColors.Cyan,
    onPrimary = Color(0xFF041014),
    secondary = OpenStreamColors.Amber,
    onSecondary = Color(0xFF1A1204),
    background = OpenStreamColors.Canvas,
    onBackground = OpenStreamColors.Text,
    surface = OpenStreamColors.Surface,
    onSurface = OpenStreamColors.Text,
    surfaceVariant = OpenStreamColors.SurfaceRaised,
    onSurfaceVariant = OpenStreamColors.TextSoft,
    error = OpenStreamColors.Danger,
)

@Composable
fun OpenStreamTheme(content: @Composable () -> Unit) {
    // The app is a media surface: always dark, regardless of system setting.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
