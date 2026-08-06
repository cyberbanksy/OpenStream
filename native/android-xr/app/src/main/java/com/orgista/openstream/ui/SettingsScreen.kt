package com.orgista.openstream.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.domain.ContentPolicy
import com.orgista.openstream.media.HdrCapabilityProbe
import com.orgista.openstream.media.MediaCapabilityReport
import com.orgista.openstream.media.SupportState
import com.orgista.openstream.library.MetadataCredentialPolicy
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
fun SettingsScreen(
    policy: ContentPolicy,
    libraryStore: com.orgista.openstream.library.LibraryStore,
    onTmdbKeyChanged: () -> Unit,
) {
    val context = LocalContext.current
    val report = remember { HdrCapabilityProbe.inspect(context) }
    var tmdbKey by remember { mutableStateOf(libraryStore.tmdbApiKey) }
    var savedKey by remember { mutableStateOf(libraryStore.tmdbApiKey) }
    var omdbKey by remember { mutableStateOf(libraryStore.omdbApiKey) }
    var savedOmdb by remember { mutableStateOf(libraryStore.omdbApiKey) }
    var autoSubs by remember { mutableStateOf(libraryStore.autoSubtitles) }
    var metadataOptIn by remember { mutableStateOf(libraryStore.metadataOptIn) }
    var savedMetadataOptIn by remember { mutableStateOf(libraryStore.metadataOptIn) }

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

        fun saveKeys() {
            libraryStore.metadataOptIn = metadataOptIn
            libraryStore.tmdbApiKey = tmdbKey
            libraryStore.omdbApiKey = omdbKey
            savedKey = tmdbKey
            savedOmdb = omdbKey
            savedMetadataOptIn = metadataOptIn
            onTmdbKeyChanged()
        }
        val dirty = tmdbKey != savedKey || omdbKey != savedOmdb || metadataOptIn != savedMetadataOptIn
        val tmdbFormatValid = tmdbKey.isBlank() || MetadataCredentialPolicy.looksLikeTmdbCredential(tmdbKey)

        Panel("Artwork & descriptions (TMDB)") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Use my metadata API credentials",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpenStreamColors.Text,
                    )
                    Text(
                        "Opt in to reuse these credentials for library scans and title details on this device.",
                        fontSize = 13.sp,
                        color = OpenStreamColors.Muted,
                    )
                }
                Switch(
                    checked = metadataOptIn,
                    onCheckedChange = { metadataOptIn = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OpenStreamColors.Accent,
                    ),
                )
            }
            Text(
                when {
                    !savedMetadataOptIn -> "Metadata access is off. Stored credentials are not used."
                    savedKey.isBlank() -> "Add a TMDB API key or read token to fetch posters, backdrops, and descriptions."
                    else -> "Posters and descriptions are enabled."
                },
                fontSize = 13.sp,
                color = OpenStreamColors.Muted,
            )
            androidx.compose.material3.OutlinedTextField(
                value = tmdbKey,
                onValueChange = { tmdbKey = it.trim() },
                singleLine = true,
                label = { Text("TMDB API key or read token") },
                placeholder = { Text("themoviedb.org → Settings → API") },
                enabled = metadataOptIn,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (!tmdbFormatValid) {
                    { Text("Enter a 32-character v3 key or a v4 read token.") }
                } else null,
                isError = !tmdbFormatValid,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpenStreamColors.Accent.copy(alpha = 0.6f),
                    unfocusedBorderColor = OpenStreamColors.Border,
                ),
            )
        }

        Panel("Ratings · Rotten Tomatoes, IMDb & Metacritic") {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (savedOmdb.isBlank()) AppIcons.Warning else AppIcons.Check,
                    contentDescription = null,
                    tint = if (savedOmdb.isBlank()) OpenStreamColors.Muted else OpenStreamColors.Green,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    if (savedOmdb.isBlank()) "Rotten Tomatoes scores are off." else "🍅 Rotten Tomatoes scores are on.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpenStreamColors.Text,
                )
            }
            Text(
                "Rotten Tomatoes has no public API of its own. Like Infuse and Jellyfin, OpenStream reads RT — plus IMDb and Metacritic — through OMDb, the free aggregator. Paste your OMDb key below and the scores appear on each title.",
                fontSize = 13.sp,
                color = OpenStreamColors.Muted,
                lineHeight = 19.sp,
            )
            androidx.compose.material3.OutlinedTextField(
                value = omdbKey,
                onValueChange = { omdbKey = it.trim() },
                singleLine = true,
                label = { Text("Rotten Tomatoes / OMDb key") },
                placeholder = { Text("omdbapi.com/apikey.aspx — free") },
                enabled = metadataOptIn,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpenStreamColors.Accent.copy(alpha = 0.6f),
                    unfocusedBorderColor = OpenStreamColors.Border,
                ),
            )
            Text(
                "Get a free key: omdbapi.com → API Key → FREE (1,000/day). They email it; paste it here.",
                fontSize = 12.sp,
                color = OpenStreamColors.Muted,
            )
        }

        androidx.compose.material3.Button(
            onClick = { saveKeys() },
            enabled = dirty && tmdbFormatValid,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = OpenStreamColors.Accent,
                contentColor = Color.White,
            ),
        ) { Text(if (!metadataOptIn) "Save with metadata off" else "Save & re-scan") }

        Panel("Captions") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Turn on subtitles automatically",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpenStreamColors.Text,
                    )
                    Text(
                        "When a video has subtitles — embedded or a matching file on the NAS — show them from the first frame.",
                        fontSize = 13.sp,
                        color = OpenStreamColors.Muted,
                        lineHeight = 18.sp,
                    )
                }
                Switch(
                    checked = autoSubs,
                    onCheckedChange = { autoSubs = it; libraryStore.autoSubtitles = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OpenStreamColors.Accent,
                    ),
                )
            }
        }

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
            Surface(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cyberbanksy")))
                },
                shape = RoundedCornerShape(10.dp),
                color = OpenStreamColors.SurfaceRaised,
            ) {
                Text(
                    "GitHub · cyberbanksy",
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OpenStreamColors.Cyan,
                )
            }
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
