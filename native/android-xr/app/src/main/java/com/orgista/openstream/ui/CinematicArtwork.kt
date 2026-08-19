package com.orgista.openstream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.orgista.openstream.data.MediaItem

/** Edge-to-edge cinematic artwork with a softly blurred loading/fallback layer. */
@Composable
fun CinematicArtwork(item: MediaItem, modifier: Modifier = Modifier) {
    val foregroundScale = when (ArtworkPresentationPolicy.cinematicArtworkFit) {
        CinematicArtworkFit.FULL_BLEED -> ContentScale.Crop
        CinematicArtworkFit.FIT_WITH_AMBIENT_BANDS -> ContentScale.Fit
    }

    Box(modifier.background(Color.Black).clipToBounds()) {
        Poster(
            item = item,
            modifier = Modifier.fillMaxSize().scale(1.12f).blur(22.dp),
            useBackdrop = true,
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        Poster(
            item = item,
            modifier = Modifier.fillMaxSize(),
            useBackdrop = true,
            contentScale = foregroundScale,
        )
    }
}
