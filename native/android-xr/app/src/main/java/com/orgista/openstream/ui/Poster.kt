package com.orgista.openstream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orgista.openstream.data.MediaItem

/** Deterministic warm-neutral tint per title so fallback posters vary. */
private fun tintFor(seed: String): Color {
    val hues = listOf(0xFF1B2430, 0xFF241B2B, 0xFF2B211B, 0xFF1B2B26, 0xFF2B1B22, 0xFF20242B)
    return Color(hues[(seed.hashCode() and 0x7fffffff) % hues.size])
}

/**
 * Renders artwork supplied by a configured source, with a clean typographic
 * fallback while source metadata is unavailable.
 */
@Composable
fun Poster(
    item: MediaItem,
    modifier: Modifier = Modifier,
    useBackdrop: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = item.title,
) {
    val remote = if (useBackdrop) item.backdropUrl ?: item.posterUrl else item.posterUrl
    when {
        remote != null -> AsyncImage(
            model = remote,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
        else -> Box(
            modifier.background(
                Brush.verticalGradient(listOf(tintFor(item.title), OpenStreamColors.CanvasDeep)),
            ),
        ) {
            Text(
                item.title,
                modifier = Modifier.align(Alignment.Center).padding(14.dp),
                color = OpenStreamColors.TextSoft,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
