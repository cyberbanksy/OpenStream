package com.orgista.openstream.ui

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
import com.orgista.openstream.R
import com.orgista.openstream.data.DEMO_URI
import com.orgista.openstream.player.SmbDataSource
import com.orgista.openstream.ui.icons.AppIcons

/**
 * Native Media3 playback. SMB URIs stream through the jcifs-ng DataSource,
 * HTTP(S) and the bundled demo use the default sources. ExoPlayer's own
 * extractors handle MKV/MP4/TS with the device's hardware decoders — the
 * formats a browser engine can't touch.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlaybackRequest, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember {
        val dataSourceFactory: DataSource.Factory = when {
            request.uri.startsWith("smb://") && request.nasConnection != null ->
                SmbDataSource.Factory(request.nasConnection)
            else -> DefaultDataSource.Factory(context)
        }
        val mediaUri = if (request.uri == DEMO_URI) {
            RawResourceDataSource.buildRawResourceUri(R.raw.openstream_demo)
        } else {
            android.net.Uri.parse(request.uri)
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(Media3Item.fromUri(mediaUri))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.errorCodeName
                    .removePrefix("ERROR_CODE_")
                    .replace('_', ' ')
                    .lowercase()
            }
        }
        player.addListener(listener)
        onDispose {
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
