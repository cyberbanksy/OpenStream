package com.orgista.openstream.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.orgista.openstream.Logs
import com.orgista.openstream.R
import com.orgista.openstream.data.DEMO_URI
import com.orgista.openstream.player.SmbDataSource
import com.orgista.openstream.player.PlaybackStore
import com.orgista.openstream.player.PlaybackFormatLabels
import com.orgista.openstream.player.PlaybackTrackSignal
import com.orgista.openstream.offline.OfflineMediaRuntime
import com.orgista.openstream.ui.icons.AppIcons

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Native Media3 playback. SMB URIs stream through the jcifs-ng DataSource,
 * HTTP(S) and the bundled demo use the default sources. Only this screen is
 * immersive; system bars come back the moment playback closes so the app
 * never traps the viewer.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    autoSubtitles: Boolean = false,
    playbackStore: PlaybackStore,
    onBack: () -> Unit,
    onExit: () -> Unit,
    onProgressSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var chromeVisible by remember { mutableStateOf(false) }
    var formatLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var formatIndicatorVisible by remember { mutableStateOf(false) }
    val offlineRuntime = remember { OfflineMediaRuntime.get(context.applicationContext) }
    val progressKey = request.mediaId ?: request.uri
    val playbackRoutes = remember(request) {
        playableUris(request).ifEmpty { listOf(request.uri) }
    }
    var activeRouteIndex by remember(request) { mutableIntStateOf(0) }
    val savedPosition = remember(request.uri, progressKey) {
        (playbackStore.load(progressKey) ?: request.uri.takeIf { it != progressKey }?.let(playbackStore::load))?.let {
            com.orgista.openstream.player.PlaybackProgressPolicy.resumePosition(it.positionMs, it.durationMs)
        } ?: 0L
    }

    fun playerUri(uri: String): android.net.Uri =
        if (uri == DEMO_URI) android.net.Uri.parse("rawresource:///${R.raw.openstream_demo}")
        else android.net.Uri.parse(uri)

    val mediaUri = remember(playbackRoutes) {
        playerUri(playbackRoutes.first())
    }
    val initialMediaItem = remember(request, mediaUri) {
        request.offlineDownloadId?.let(offlineRuntime::downloadMediaItem)
            ?: Media3Item.Builder()
                .setUri(mediaUri)
                .apply {
                    request.offlineDownloadId?.let { setCustomCacheKey("offline-$it") }
                }
                .build()
    }

    val player = remember(request, playbackRoutes) {
        val dataSourceFactory: DataSource.Factory = when {
            request.offlineDownloadId != null -> offlineRuntime.cacheOnlyDataSourceFactory()
            request.uri.startsWith("smb://") && request.nasConnection != null ->
                SmbDataSource.Factory(request.nasConnection)
            else -> DefaultDataSource.Factory(context)
        }
        // Hardware decoders first — this preserves Dolby Vision video and
        // Dolby Atmos (TrueHD/E-AC3) passthrough that this device supports
        // natively — with the FFmpeg software decoders as a *fallback* only
        // for codecs the hardware can't handle. MODE_ON = hardware first,
        // extension fallback (not MODE_PREFER, which would force software and
        // lose Atmos/DV). This is the Infuse/Jellyfin behaviour.
        val renderersFactory = io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory(context)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                setMediaItem(initialMediaItem)
                if (savedPosition > 0L) seekTo(savedPosition)
                playWhenReady = true
                prepare()
            }
    }

    LaunchedEffect(request.mediaId) {
        request.mediaId?.let(playbackStore::markStarted)
    }

    // Side-load subtitle files sitting next to the video on the NAS (Movie.srt,
    // Movie.en.srt …). Embedded subtitles inside the MKV are picked up
    // automatically; this adds external ones. Re-sets the media item with the
    // subtitle tracks once found (still near position 0, before real playback).
    LaunchedEffect(request.uri) {
        if (request.offlineDownloadId != null) return@LaunchedEffect
        val connection = request.nasConnection ?: return@LaunchedEffect
        if (!request.uri.startsWith("smb://")) return@LaunchedEffect
        val relative = request.uri.removePrefix(com.orgista.openstream.data.Smb.shareUrl(connection))
        val subs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.orgista.openstream.data.Smb.siblingSubtitles(connection, relative) }.getOrDefault(emptyList())
        }
        if (subs.isEmpty()) return@LaunchedEffect
        Logs.event("side-loaded ${subs.size} subtitle track(s) for ${request.title}")
        val item = Media3Item.Builder()
            .setUri(mediaUri)
            .setSubtitleConfigurations(
                subs.map { sub ->
                    Media3Item.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.uri))
                        .setMimeType(sub.mimeType)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .build()
                },
            )
            .build()
        val resume = player.currentPosition.coerceAtLeast(savedPosition)
        player.setMediaItem(item, resume)
        player.playWhenReady = true
        player.prepare()
    }

    // Save in small intervals so an activity or gesture-back transition cannot
    // lose the resume point. The URI is hashed by PlaybackStore.
    LaunchedEffect(player, progressKey) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            if (player.duration > 0L) {
                playbackStore.save(progressKey, player.currentPosition, player.duration)
            }
        }
    }

    LaunchedEffect(formatLabels) {
        if (formatLabels.isNotEmpty()) {
            formatIndicatorVisible = true
            kotlinx.coroutines.delay(3_500)
            formatIndicatorVisible = false
        }
    }

    // Keep the status bar hidden while playing, but leave the navigation bar
    // shown so swipe-up-to-Home keeps working — matching the app-wide behaviour
    // in NativeAppActivity. Critically, do NOT hide the nav bar on dispose:
    // doing so left the whole app in immersive-sticky and broke the Home
    // gesture on the browse screens after a video closed.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        fun applyBars() {
            if (window == null) return
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.hide(WindowInsets.Type.statusBars())
                window.insetsController?.show(WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
        applyBars()
        onDispose { applyBars() }
    }

    DisposableEffect(Unit) {
        var lastLoggedTrackSummary: String? = null
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Logs.event("playback ${if (isPlaying) "playing" else "paused"}: ${request.title}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    error = null
                    Logs.event("playback ready: ${request.title}")
                } else if (playbackState == Player.STATE_ENDED) {
                    playbackStore.clear(progressKey)
                    if (progressKey != request.uri) playbackStore.clear(request.uri)
                    request.mediaId?.let { playbackStore.markCompleted(it) }
                }
            }

            // Auto-captions: when the setting is on and nothing has selected a
            // text track yet, force-select the first available subtitle track
            // (embedded or side-loaded). The isSelected guard stops the
            // parameter change from looping through onTracks.
            override fun onTracksChanged(tracks: Tracks) {
                fun selectedFormat(type: Int): androidx.media3.common.Format? = tracks.groups
                    .asSequence()
                    .filter { it.type == type }
                    .flatMap { group ->
                        (0 until group.length).asSequence()
                            .filter(group::isTrackSelected)
                            .map(group::getTrackFormat)
                    }
                    .firstOrNull()
                val video = selectedFormat(C.TRACK_TYPE_VIDEO)
                val audio = selectedFormat(C.TRACK_TYPE_AUDIO)
                val text = selectedFormat(C.TRACK_TYPE_TEXT)
                val trackSummary = listOf(
                    "video=${video?.sampleMimeType ?: "none"}",
                    "audio=${audio?.sampleMimeType ?: "none"}",
                    "captions=${text?.language ?: if (text == null) "off" else "und"}",
                ).joinToString(",")
                if (trackSummary != lastLoggedTrackSummary) {
                    lastLoggedTrackSummary = trackSummary
                    Logs.event("selected tracks: $trackSummary")
                }
                formatLabels = PlaybackFormatLabels.forSignal(
                    PlaybackTrackSignal(
                        videoCodecs = video?.codecs,
                        colorTransfer = video?.colorInfo?.colorTransfer,
                        hasHdrStaticInfo = video?.colorInfo?.hdrStaticInfo != null,
                        audioMimeType = audio?.sampleMimeType,
                        audioCodecs = audio?.codecs,
                        sourceHint = request.subtitle,
                    ),
                )
                if (!autoSubtitles) return
                val alreadyOn = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
                if (alreadyOn) return
                val textGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                    ?: return
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(textGroup.mediaTrackGroup, 0))
                    .build()
                Logs.event("auto-captions: subtitle track enabled for ${request.title}")
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                // PlaybackException causes may include signed media URLs.
                val nextRoute = activeRouteIndex + 1
                if (nextRoute < playbackRoutes.size) {
                    val resume = player.currentPosition.coerceAtLeast(savedPosition)
                    activeRouteIndex = nextRoute
                    error = null
                    Logs.warn(
                        "playback route unavailable; trying alternate " +
                            "${nextRoute + 1}/${playbackRoutes.size} for ${request.title}",
                    )
                    player.setMediaItem(Media3Item.fromUri(playerUri(playbackRoutes[nextRoute])), resume)
                    player.playWhenReady = true
                    player.prepare()
                } else {
                    Logs.error("playback error for ${request.title}: ${playbackError.errorCodeName}")
                    error = playbackError.errorCodeName
                        .removePrefix("ERROR_CODE_")
                        .replace('_', ' ')
                        .lowercase()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (player.duration > 0L) {
                playbackStore.save(progressKey, player.currentPosition, player.duration)
            }
            Logs.event("playback closed: ${request.title}")
            player.removeListener(listener)
            player.release()
            onProgressSaved()
        }
    }

    // Android 17 hardens background media starts. OpenStream playback is
    // always user initiated, and it pauses/saves as soon as the activity is no
    // longer visible instead of continuing unexpectedly in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player, progressKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (player.duration > 0L) {
                    playbackStore.save(progressKey, player.currentPosition, player.duration)
                }
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var swipeStartNearBottom by remember { mutableStateOf(false) }
    var upwardDrag by remember { mutableFloatStateOf(0f) }
    var exitTriggered by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(onExit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        swipeStartNearBottom = offset.y >= size.height * 0.72f
                        upwardDrag = 0f
                        exitTriggered = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!swipeStartNearBottom || exitTriggered) return@detectVerticalDragGestures
                        upwardDrag += -dragAmount
                        if (upwardDrag >= size.height * 0.12f) {
                            exitTriggered = true
                            change.consume()
                            Logs.event("exit to launcher (swipe up from player)")
                            onExit()
                        }
                    },
                )
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    // CC button opens the subtitle-track picker (embedded +
                    // side-loaded); the settings menu covers audio tracks.
                    setShowSubtitleButton(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerShowTimeoutMs = 3500
                    controllerAutoShow = false
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            chromeVisible = visibility == View.VISIBLE
                        },
                    )
                    post { hideController() }
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Title, source, and back are part of the transient controller chrome:
        // touch or remote input reveals them; they never sit over the movie.
        if (chromeVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.60f), Color.Transparent)))
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.padding(start = 4.dp)) {
                    Text(
                        request.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    request.subtitle?.let { location ->
                        Text(
                            "Playing from $location",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        if (formatIndicatorVisible && formatLabels.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    formatLabels.forEachIndexed { index, label ->
                        if (index > 0) Text("•", color = Color.White.copy(alpha = 0.55f))
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        val message = error
        if (message != null) {
            Text(
                "Playback failed: $message. Try another title or source.",
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                color = OpenStreamColors.Danger,
            )
        }
    }
}
