package com.orgista.openstream.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Restrained, cinema-grade palette: warm near-blacks and off-white text with
 * a single muted-gold accent used sparingly for selection, focus, and
 * progress. No competing bright accents — the artwork carries the colour.
 */
object OpenStreamColors {
    val CanvasDeep = Color(0xFF0A0A0C)
    val Canvas = Color(0xFF0E0E11)
    val Surface = Color(0xFF17171B)
    val SurfaceRaised = Color(0xFF212128)
    val Text = Color(0xFFF2F1ED)
    val TextSoft = Color(0xFFC4C3BD)
    val Muted = Color(0xFF86858C)
    val Border = Color(0x14F2F1ED)
    val BorderStrong = Color(0x2EF2F1ED)

    /** The one accent. Champagne gold; premium, not neon. */
    val Accent = Color(0xFFD9B678)
    val AccentSoft = Color(0x1FD9B678)

    val Danger = Color(0xFFE0695E)

    // Back-compat aliases so existing call sites keep compiling; all resolve
    // into the restrained palette above.
    val Cyan = Accent
    val Amber = Accent
    val Coral = Muted
    val Green = TextSoft
}

private val scheme = darkColorScheme(
    primary = OpenStreamColors.Accent,
    onPrimary = Color(0xFF14110A),
    secondary = OpenStreamColors.Accent,
    onSecondary = Color(0xFF14110A),
    background = OpenStreamColors.Canvas,
    onBackground = OpenStreamColors.Text,
    surface = OpenStreamColors.Surface,
    onSurface = OpenStreamColors.Text,
    surfaceVariant = OpenStreamColors.SurfaceRaised,
    onSurfaceVariant = OpenStreamColors.TextSoft,
    error = OpenStreamColors.Danger,
    outline = OpenStreamColors.BorderStrong,
)

@Composable
fun OpenStreamTheme(content: @Composable () -> Unit) {
    // Always dark: this is a media surface, not a system-themed app.
    MaterialTheme(colorScheme = scheme, content = content)
}
