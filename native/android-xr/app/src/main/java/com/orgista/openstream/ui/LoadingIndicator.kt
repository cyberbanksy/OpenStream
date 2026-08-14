package com.orgista.openstream.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

enum class LoadingIndicatorRole {
    BUTTON,
    INLINE,
    CONTENT,
    PLAYBACK,
    DOWNLOAD,
}

@Composable
internal fun OpenStreamLoadingLabel(
    label: String,
    modifier: Modifier = Modifier,
    role: LoadingIndicatorRole = LoadingIndicatorRole.BUTTON,
    indicatorColor: Color = OpenStreamColors.Accent,
    labelColor: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpenStreamLoadingIndicator(role = role, color = indicatorColor)
        Text(label, color = labelColor)
    }
}

data class LoadingIndicatorMetrics(
    val diameterDp: Int,
    val strokeWidthDp: Int,
    val roundStroke: Boolean = true,
    val centerInContainer: Boolean = false,
)

object LoadingIndicatorPolicy {
    fun metrics(role: LoadingIndicatorRole): LoadingIndicatorMetrics = when (role) {
        LoadingIndicatorRole.BUTTON -> LoadingIndicatorMetrics(16, 2)
        LoadingIndicatorRole.INLINE -> LoadingIndicatorMetrics(14, 2)
        LoadingIndicatorRole.CONTENT -> LoadingIndicatorMetrics(32, 3, centerInContainer = true)
        LoadingIndicatorRole.PLAYBACK -> LoadingIndicatorMetrics(28, 3, centerInContainer = true)
        LoadingIndicatorRole.DOWNLOAD -> LoadingIndicatorMetrics(19, 2)
    }
}

@Composable
internal fun OpenStreamLoadingIndicator(
    modifier: Modifier = Modifier,
    role: LoadingIndicatorRole = LoadingIndicatorRole.BUTTON,
    color: Color = OpenStreamColors.Accent,
    trackColor: Color = color.copy(alpha = 0.18f),
    progress: Float? = null,
) {
    val metrics = LoadingIndicatorPolicy.metrics(role)
    val sizedModifier = modifier.size(metrics.diameterDp.dp)
    val strokeCap = if (metrics.roundStroke) StrokeCap.Round else StrokeCap.Butt
    if (progress == null) {
        CircularProgressIndicator(
            modifier = sizedModifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = metrics.strokeWidthDp.dp,
            strokeCap = strokeCap,
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = sizedModifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = metrics.strokeWidthDp.dp,
            strokeCap = strokeCap,
        )
    }
}
