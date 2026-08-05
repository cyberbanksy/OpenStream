package com.orgista.openstream.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.orgista.openstream.Logs
import com.orgista.openstream.R
import com.orgista.openstream.data.DEMO_URI
import com.orgista.openstream.player.SmbDataSource
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
fun PlayerScreen(request: PlaybackRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    val mediaUri = remember(request.uri) {
        if (request.uri == DEMO_URI) RawResourceDataSource.buildRawResourceUri(R.raw.openstream_demo)
        else android.net.Uri.parse(request.uri)
    }

    val player = remember {
        val dataSourceFactory: DataSource.Factory = when {
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
                setMediaItem(Media3Item.fromUri(mediaUri))
                playWhenReady = true
                prepare()
            }
    }

    // Side-load subtitle files sitting next to the video on the NAS (Movie.srt,
    // Movie.en.srt …). Embedded subtitles inside the MKV are picked up
    // automatically; this adds external ones. Re-sets the media item with the
    // subtitle tracks once found (still near position 0, before real playback).
    androidx.compose.runtime.LaunchedEffect(request.uri) {
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
        val resume = player.currentPosition
        player.setMediaItem(item, resume)
        player.prepare()
    }

    // The whole app is already immersive (NativeAppActivity), so the player is
    // fullscreen without touching the bars here. Re-assert the hide on enter in
    // case a transient swipe revealed them, and — importantly — re-hide on exit
    // so returning to a browse screen doesn't leave the system bars / taskbar
    // handle showing.
    DisposableEffect(Unit) {
        val insetsController = context.findActivity()?.window?.insetsController
        fun hideBars() {
            insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        hideBars()
        onDispose { hideBars() }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    Logs.event("playback ready: ${request.title}")
                }
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                Logs.error("playback error for ${request.title}: ${playbackError.errorCodeName}", playbackError)
                error = playbackError.errorCodeName
                    .removePrefix("ERROR_CODE_")
                    .replace('_', ' ')
                    .lowercase()
            }
        }
        player.addListener(listener)
        onDispose {
            Logs.event("playback closed: ${request.title}")
            player.removeListener(listener)
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                    controllerShowTimeoutMs = 3500
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(10.dp)) {
            Icon(AppIcons.Back, contentDescription = "Back", tint = Color.White)
        }
        Text(
            request.title + (request.subtitle?.let { " · $it" } ?: ""),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
            color = Color.White.copy(alpha = 0.85f),
        )
        val message = error
        if (message != null) {
            Text(
                "Playback failed: $message. This device's decoders may not support the format.",
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                color = OpenStreamColors.Danger,
            )
        }
    }
}
