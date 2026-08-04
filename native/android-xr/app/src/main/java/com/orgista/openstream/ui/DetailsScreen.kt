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
    metadataLoader: com.orgista.openstream.library.MetadataLoader,
    onBack: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var streamResults by remember { mutableStateOf<List<StremioStream>?>(null) }
    var searching by remember { mutableStateOf(false) }

    // Lazily fetch full metadata (genres, cast, RT/IMDb/Metacritic) for this
    // one title when the screen opens — keeps the library scan fast.
    var enriched by remember(item.id) { mutableStateOf(item) }
    androidx.compose.runtime.LaunchedEffect(item.id) {
        if (metadataLoader.active && item.tmdbId != null) {
            runCatching { metadataLoader.enrich(item) }.getOrNull()?.let { enriched = it }
        }
    }
    val item = enriched

    Column(Modifier.fillMaxSize().background(OpenStreamColors.Canvas).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            Poster(item, Modifier.fillMaxSize(), useBackdrop = true)
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
            Text(item.title, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
            if (item.tagline != null) {
                Text(item.tagline, color = OpenStreamColors.Muted, fontSize = 15.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            MetaRow(item, includeFormat = false)

            item.ratings?.let { ratings ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ratings.rottenTomatoes?.let { ScoreBadge("🍅", it, OpenStreamColors.Coral) }
                    ratings.imdb?.let { ScoreBadge("IMDb", it, OpenStreamColors.Accent) }
                    ratings.metacritic?.let { ScoreBadge("Metacritic", it, OpenStreamColors.TextSoft) }
                }
            }

            if (item.genres.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.genres.take(4).forEach { genre ->
                        Surface(shape = RoundedCornerShape(50), color = OpenStreamColors.SurfaceRaised) {
                            Text(genre, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, color = OpenStreamColors.TextSoft)
                        }
                    }
                }
            }

            if (item.subtitle != null) Text(item.subtitle, color = OpenStreamColors.Muted, fontSize = 14.sp)
            Text(item.synopsis, fontSize = 16.sp, lineHeight = 24.sp, color = OpenStreamColors.TextSoft)

            if (item.director != null) {
                Text("Director  ${item.director}", fontSize = 14.sp, color = OpenStreamColors.Muted)
            }
            if (item.cast.isNotEmpty()) {
                Text("Cast  ${item.cast.take(5).joinToString(", ")}", fontSize = 14.sp, color = OpenStreamColors.Muted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val first = item.candidates.firstOrNull()
                val playLabel = if (item.episodes.isNotEmpty()) "Play S1 E1" else "Play"
                PillButton(playLabel, icon = AppIcons.Play, enabled = first != null) {
                    if (first != null) {
                        onPlay(PlaybackRequest(item.title, item.episodes.firstOrNull()?.subtitle ?: first.sourceName, first.uri))
                    }
                }
                if (!kidsMode && addons.isNotEmpty() && item.imdbId != null) {
                    PillButton(if (searching) "Searching…" else "Search add-ons", prominent = false, enabled = !searching) {
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
                    }
                }
            }

            if (item.episodes.isNotEmpty()) {
                SectionHeader("Episodes")
                item.episodes.forEach { episode ->
                    val epCandidate = episode.candidates.firstOrNull()
                    Surface(
                        onClick = { epCandidate?.let { onPlay(PlaybackRequest("${item.title} · ${episode.subtitle}", item.title, it.uri)) } },
                        shape = RoundedCornerShape(10.dp),
                        color = OpenStreamColors.Surface,
                        border = BorderStroke(1.dp, OpenStreamColors.Border),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Play, contentDescription = null, tint = OpenStreamColors.Accent, modifier = Modifier.size(18.dp))
                            Text(episode.subtitle ?: "Episode", fontWeight = FontWeight.Bold, color = OpenStreamColors.Text)
                            Text(epCandidate?.formatLabel ?: "", fontSize = 12.sp, color = OpenStreamColors.Muted, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            SectionHeader("Available routes")
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

@Composable
private fun ScoreBadge(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = OpenStreamColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OpenStreamColors.Border),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
        }
    }
}
