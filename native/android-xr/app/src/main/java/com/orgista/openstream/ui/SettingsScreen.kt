package com.orgista.openstream.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.domain.ContentPolicy
import com.orgista.openstream.media.HdrCapabilityProbe
import com.orgista.openstream.media.MediaCapabilityReport
import com.orgista.openstream.media.SupportState
import com.orgista.openstream.ui.icons.AppIcons

private fun rangeLabel(range: MediaCapabilityReport.DynamicRange): String = when (range) {
    MediaCapabilityReport.DynamicRange.SDR -> "SDR"
    MediaCapabilityReport.DynamicRange.HDR10 -> "HDR10"
    MediaCapabilityReport.DynamicRange.HDR10_PLUS -> "HDR10+"
    MediaCapabilityReport.DynamicRange.HLG -> "HLG"
    MediaCapabilityReport.DynamicRange.DOLBY_VISION -> "Dolby Vision"
}

private fun stateLabel(state: SupportState): String = when (state) {
    SupportState.SUPPORTED -> "Supported"
    SupportState.UNSUPPORTED -> "Unsupported"
    SupportState.RUNTIME_CHECK -> "Check on play"
}

private fun stateColor(state: SupportState): Color = when (state) {
    SupportState.SUPPORTED -> OpenStreamColors.Green
    SupportState.UNSUPPORTED -> OpenStreamColors.Danger
    SupportState.RUNTIME_CHECK -> OpenStreamColors.Amber
}

/** Device capability report (real decoder/display probes) plus family status. */
@Composable
fun SettingsScreen(policy: ContentPolicy) {
    val context = LocalContext.current
    val report = remember { HdrCapabilityProbe.inspect(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Settings",
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OpenStreamColors.Text,
        )

        Panel("Family & content") {
            when (policy) {
                is ContentPolicy.Kids -> {
                    InfoRow(AppIcons.Lock, "Kids Mode", "Permanently on · ${policy.cap.label} (${policy.cap.examples})", OpenStreamColors.Amber)
                    InfoRow(AppIcons.Check, "Unrated content", "Always blocked", OpenStreamColors.Green)
                    InfoRow(
                        AppIcons.Check,
                        "Blocked terms",
                        if (policy.blockedTerms.isEmpty()) "None" else "${policy.blockedTerms.size} hidden",
                        OpenStreamColors.Green,
                    )
                    Text(
                        "Turning Kids Mode off requires uninstalling OpenStream and setting it up again.",
                        fontSize = 13.sp,
                        color = OpenStreamColors.Muted,
                    )
                }
                ContentPolicy.Standard -> InfoRow(AppIcons.Check, "Kids Mode", "Off — chosen at setup", OpenStreamColors.Muted)
            }
        }

        Panel("Playback (decoder & display probes)") {
            MediaCapabilityReport.DynamicRange.entries.forEach { range ->
                val state = report.playableState(range)
                InfoRow(
                    icon = if (state == SupportState.SUPPORTED) AppIcons.Check else AppIcons.Warning,
                    title = rangeLabel(range),
                    detail = stateLabel(state),
                    tint = stateColor(state),
                )
            }
            InfoRow(
                AppIcons.Check,
                "HEVC Main10",
                if (report.hasHevcMain10Decoder()) "Hardware decoder present" else "No decoder",
                if (report.hasHevcMain10Decoder()) OpenStreamColors.Green else OpenStreamColors.Danger,
            )
            InfoRow(
                AppIcons.Check,
                "AV1 Main10",
                if (report.hasAv1Main10Decoder()) "Hardware decoder present" else "No decoder",
                if (report.hasAv1Main10Decoder()) OpenStreamColors.Green else OpenStreamColors.Danger,
            )
        }

        Panel("About") {
            InfoRow(AppIcons.Home, "OpenStream", "Native Android · Media3 playback · direct SMB", OpenStreamColors.Cyan)
            Text(
                "NAS files play natively; formats the device cannot decode are reported before playback rather than silently failing.",
                fontSize = 13.sp,
                color = OpenStreamColors.Muted,
            )
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OpenStreamColors.Surface,
        border = BorderStroke(1.dp, OpenStreamColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
            content()
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    tint: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(title, fontWeight = FontWeight.Bold, color = OpenStreamColors.Text, fontSize = 14.sp)
        Text(detail, color = OpenStreamColors.Muted, fontSize = 13.sp)
    }
}
