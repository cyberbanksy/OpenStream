package com.orgista.openstream.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.StremioAddon
import com.orgista.openstream.data.StremioClient
import com.orgista.openstream.data.StremioStream
import com.orgista.openstream.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailsScreen(
    item: MediaItem,
    kidsMode: Boolean,
    addons: List<StremioAddon>,
    onBack: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var streamResults by remember { mutableStateOf<List<StremioStream>?>(null) }
    var searching by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(OpenStreamColors.Canvas).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            if (item.artRes != null) {
                Image(
                    painter = painterResource(item.artRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to OpenStreamColors.Canvas,
                    ),
                ),
            )
            IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) {
                Icon(AppIcons.Back, contentDescription = "Back", tint = OpenStreamColors.Text)
            }
        }

        Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.title, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${item.year}", color = OpenStreamColors.TextSoft)
                RatingChip(item.rating)
                Text(formatRuntime(item.runtimeMinutes), color = OpenStreamColors.TextSoft)
                if (item.subtitle != null) Text(item.subtitle, color = OpenStreamColors.Muted)
            }
            Text(item.synopsis, fontSize = 16.sp, lineHeight = 24.sp, color = OpenStreamColors.TextSoft)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val first = item.candidates.firstOrNull()
                Button(
                    onClick = {
                        if (first != null) {
                            onPlay(PlaybackRequest(item.title, first.sourceName, first.uri))
                        }
                    },
                    enabled = first != null,
                ) {
                    Icon(AppIcons.Play, contentDescription = null)
                    Text(" Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (!kidsMode && addons.isNotEmpty() && item.imdbId != null) {
                    OutlinedButton(
                        onClick = {
                            searching = true
                            scope.launch {
                                val type = if (item.kind == MediaKind.SERIES) "series" else "movie"
                                val found = withContext(Dispatchers.IO) {
                                    addons.flatMap { addon ->
                                        runCatching { StremioClient.streamsFor(addon, type, item.imdbId) }
                                            .getOrDefault(emptyList())
                                    }
                                }
                                searching = false
                                if (found.isEmpty()) {
                                    Toast.makeText(context, "No add-on streams found", Toast.LENGTH_SHORT).show()
                                } else {
                                    streamResults = found
                                }
                            }
                        },
                        enabled = !searching,
                    ) {
                        if (searching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  Searching…")
                        } else {
                            Text("Search add-ons")
                        }
                    }
                }
            }

            Text("Available routes", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Muted)
            item.candidates.forEach { candidate ->
                Surface(
                    onClick = { onPlay(PlaybackRequest(item.title, candidate.sourceName, candidate.uri)) },
                    shape = RoundedCornerShape(10.dp),
                    color = OpenStreamColors.Surface,
                    border = BorderStroke(1.dp, OpenStreamColors.Border),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(candidate.sourceName, fontWeight = FontWeight.Bold, color = OpenStreamColors.Text)
                            Text(
                                if (candidate.directPlay) "Direct play" else "Transcode",
                                fontSize = 13.sp,
                                color = OpenStreamColors.Muted,
                            )
                        }
                        Text(candidate.formatLabel, fontSize = 13.sp, color = OpenStreamColors.TextSoft)
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }

    val results = streamResults
    if (results != null) {
        AlertDialog(
            onDismissRequest = { streamResults = null },
            confirmButton = {
                TextButton(onClick = { streamResults = null }) { Text("Close") }
            },
            title = { Text("Add-on streams") },
            text = {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    results.forEach { stream ->
                        Surface(
                            onClick = {
                                streamResults = null
                                onPlay(PlaybackRequest(item.title, "Add-on stream", stream.url))
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = OpenStreamColors.SurfaceRaised,
                        ) {
                            Text(stream.title, Modifier.fillMaxWidth().padding(12.dp), fontSize = 14.sp)
                        }
                    }
                }
            },
        )
    }
}
