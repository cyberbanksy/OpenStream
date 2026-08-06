package com.orgista.openstream.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.OptIn
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
import androidx.compose.runtime.collectAsState
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
import com.orgista.openstream.data.StremioCatalogMerger
import com.orgista.openstream.data.StremioStream
import com.orgista.openstream.Logs
import com.orgista.openstream.offline.OfflineMediaRuntime
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.orgista.openstream.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.detailsActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.detailsActivity()
    else -> null
}

private fun requestDownloadNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        context.detailsActivity()?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4701)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun DetailsScreen(
    item: MediaItem,
    kidsMode: Boolean,
    addons: List<StremioAddon>,
    offlineRuntime: OfflineMediaRuntime,
    online: Boolean,
    metadataLoader: com.orgista.openstream.library.MetadataLoader,
    onMetadataEnriched: (MediaItem) -> Unit = {},
    onBack: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var streamResults by remember { mutableStateOf<List<StremioStream>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var offlineWorking by remember { mutableStateOf(false) }
    val offlineEntries by offlineRuntime.entries.collectAsState()

    // Lazily fetch full metadata (genres, cast, RT/IMDb/Metacritic) for this
    // one title when the screen opens — keeps the library scan fast.
    var enriched by remember(item.id) { mutableStateOf(item) }
    androidx.compose.runtime.LaunchedEffect(item.id) {
        var result = item
        if (online && metadataLoader.active) {
            runCatching { metadataLoader.enrich(result) }.getOrNull()?.let { result = it }
        }
        val metaId = result.imdbId ?: result.id.takeIf { it.startsWith("tt") }
        if (online && metaId != null) {
            val type = if (result.kind == MediaKind.SERIES) "series" else "movie"
            val remoteMeta = withContext(Dispatchers.IO) {
                addons.asSequence()
                    .filter { "meta" in it.resources }
                    .mapNotNull { addon -> runCatching { StremioClient.metaFor(addon, type, metaId) }.getOrNull() }
                    .firstOrNull()
            }
            if (remoteMeta != null) result = StremioCatalogMerger.mergeItem(result, remoteMeta)
        }
        enriched = result
        onMetadataEnriched(result)
    }
    val item = enriched
    val streamAddons = addons.filter { "stream" in it.resources }

    suspend fun resolveStreams(): List<StremioStream> {
        val imdbId = item.imdbId ?: return emptyList()
        val type = if (item.kind == MediaKind.SERIES) "series" else "movie"
        return withContext(Dispatchers.IO) {
            streamAddons.flatMap { addon ->
                runCatching { StremioClient.streamsFor(addon, type, imdbId) }.getOrDefault(emptyList())
            }.distinctBy { it.url }
        }
    }

    suspend fun offlineStreams(): List<StremioStream> {
        val direct = item.candidates.mapNotNull { candidate ->
            candidate.uri.takeIf { it.startsWith("https://") || it.startsWith("http://") || it.startsWith("smb://") }
                ?.let { StremioStream("${candidate.formatLabel} · ${candidate.sourceName}", it) }
        }
        val remote = if (online && !kidsMode && streamAddons.isNotEmpty()) resolveStreams() else emptyList()
        return (direct + remote).distinctBy { it.url }
    }

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
                val canResolveStream = !kidsMode && streamAddons.isNotEmpty() && item.imdbId != null
                PillButton(playLabel, icon = AppIcons.Play, enabled = first != null || canResolveStream) {
                    if (first != null) {
                        onPlay(
                            PlaybackRequest(
                                item.title,
                                item.episodes.firstOrNull()?.subtitle ?: first.sourceName,
                                first.uri,
                                mediaId = item.id,
                                offlineDownloadId = first.offlineDownloadId,
                            ),
                        )
                    } else if (canResolveStream && !searching) {
                        searching = true
                        scope.launch {
                            val found = resolveStreams()
                            searching = false
                            val stream = found.firstOrNull()
                            if (stream == null) {
                                Toast.makeText(context, "No directly playable add-on stream found", Toast.LENGTH_SHORT).show()
                            } else {
                                onPlay(
                                    PlaybackRequest(
                                        item.title,
                                        stream.title,
                                        stream.url,
                                        mediaId = item.id,
                                        fallbackUris = found.drop(1).map { it.url },
                                    ),
                                )
                            }
                        }
                    }
                }
                if (canResolveStream) {
                    PillButton(if (searching) "Searching…" else "Search add-ons", prominent = false, enabled = !searching) {
                        searching = true
                        scope.launch {
                            val found = resolveStreams()
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

            val offlineEntry = offlineEntries.firstOrNull { it.item.id == item.id }
            val offlineLabel = when (offlineEntry?.state) {
                Download.STATE_COMPLETED -> "Remove offline download"
                Download.STATE_DOWNLOADING -> "${offlineEntry.percent ?: 0}% downloaded · Cancel"
                Download.STATE_QUEUED, Download.STATE_RESTARTING -> "Queued for offline · Cancel"
                Download.STATE_REMOVING -> "Removing…"
                Download.STATE_FAILED -> "Retry offline download"
                else -> if (offlineWorking) "Choosing best quality…" else "Make available offline"
            }
            PillButton(
                offlineLabel,
                icon = AppIcons.Storage,
                prominent = false,
                enabled = !offlineWorking && offlineEntry?.state != Download.STATE_REMOVING,
            ) {
                when (offlineEntry?.state) {
                    Download.STATE_COMPLETED,
                    Download.STATE_DOWNLOADING,
                    Download.STATE_QUEUED,
                    Download.STATE_RESTARTING,
                    -> offlineRuntime.remove(offlineEntry.downloadId)
                    else -> {
                        requestDownloadNotificationPermission(context)
                        offlineWorking = true
                        scope.launch {
                            val routes = offlineStreams()
                            val result = runCatching { offlineRuntime.enqueue(item, routes) }
                            offlineWorking = false
                            result.onSuccess { selected ->
                                Logs.event("offline download queued: ${item.title} (${selected.qualityLabel})")
                                Toast.makeText(
                                    context,
                                    "Downloading ${selected.qualityLabel} to this device",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure { failure ->
                                val location = failure.stackTrace.firstOrNull {
                                    it.className.startsWith("com.orgista.openstream") || it.className.startsWith("androidx.media3")
                                }?.let { " at ${it.className.substringAfterLast('.')}.${it.methodName}" }.orEmpty()
                                Logs.warn(
                                    "offline download could not start for ${item.title} " +
                                        "(${failure::class.simpleName}$location)",
                                )
                                Toast.makeText(context, "The offline download could not start", Toast.LENGTH_LONG).show()
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
                        onClick = {
                            epCandidate?.let {
                                onPlay(
                                    PlaybackRequest(
                                        "${item.title} · ${episode.subtitle}",
                                        item.title,
                                        it.uri,
                                        mediaId = episode.id,
                                        offlineDownloadId = it.offlineDownloadId,
                                    ),
                                )
                            }
                        },
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
                    onClick = {
                        onPlay(
                            PlaybackRequest(
                                item.title,
                                candidate.sourceName,
                                candidate.uri,
                                mediaId = item.id,
                                offlineDownloadId = candidate.offlineDownloadId,
                            ),
                        )
                    },
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
                                onPlay(PlaybackRequest(item.title, stream.title, stream.url, mediaId = item.id))
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
