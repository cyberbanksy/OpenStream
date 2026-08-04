package com.orgista.openstream.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.ui.icons.AppIcons

fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours == 0) "${rest}m" else "${hours}h ${rest.toString().padStart(2, '0')}m"
}

@Composable
fun RatingChip(rating: String, modifier: Modifier = Modifier) {
    if (rating.isBlank()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        color = Color(0xC704080A),
        border = BorderStroke(1.dp, Color(0x47FFFFFF)),
    ) {
        Text(
            rating,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OpenStreamColors.TextSoft,
        )
    }
}

@Composable
fun MediaCard(item: MediaItem, width: androidx.compose.ui.unit.Dp, onOpen: (MediaItem) -> Unit) {
    Column(Modifier.width(width)) {
        Surface(
            onClick = { onOpen(item) },
            shape = RoundedCornerShape(10.dp),
            color = OpenStreamColors.Surface,
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                if (item.artRes != null) {
                    Image(
                        painter = painterResource(item.artRes),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                RatingChip(item.rating, Modifier.align(Alignment.TopEnd).padding(8.dp))
                if (item.progressPercent != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(item.progressPercent / 100f)
                            .height(4.dp)
                            .background(OpenStreamColors.Cyan),
                    )
                }
            }
        }
        Text(
            item.title,
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = OpenStreamColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.subtitle ?: "${item.year}",
            fontSize = 12.sp,
            color = OpenStreamColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DiscoverScreen(catalog: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    if (catalog.isEmpty()) {
        EmptyCatalog()
        return
    }
    var featured by remember(catalog) { mutableStateOf(catalog.first()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            if (featured.artRes != null) {
                Image(
                    painter = painterResource(featured.artRes!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to OpenStreamColors.Canvas,
                        0.55f to OpenStreamColors.Canvas.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dp).fillMaxWidth(0.62f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    if (featured.kind == MediaKind.SERIES) "SERIES" else "FEATURE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OpenStreamColors.Coral,
                )
                Text(featured.title, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text, lineHeight = 44.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${featured.year}", color = OpenStreamColors.TextSoft, fontSize = 15.sp)
                    RatingChip(featured.rating)
                    Text(formatRuntime(featured.runtimeMinutes), color = OpenStreamColors.TextSoft, fontSize = 15.sp)
                    Text(featured.candidates.firstOrNull()?.formatLabel ?: "", color = OpenStreamColors.Muted, fontSize = 14.sp)
                }
                Text(
                    featured.synopsis,
                    fontSize = 15.sp,
                    color = OpenStreamColors.TextSoft,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(onClick = { onOpen(featured) }) {
                    Icon(AppIcons.Play, contentDescription = null)
                    Text(" Open", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "Continue watching",
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OpenStreamColors.Text,
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(catalog.filter { it.progressPercent != null }.ifEmpty { catalog }, key = { it.id }) { item ->
                Box(Modifier.clip(RoundedCornerShape(10.dp))) {
                    MediaCard(item, width = 150.dp) {
                        featured = it
                        onOpen(it)
                    }
                }
            }
        }

        Text(
            "All titles",
            modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 12.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OpenStreamColors.Text,
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = 20.dp),
        ) {
            items(catalog, key = { "all-${it.id}" }) { item ->
                MediaCard(item, width = 150.dp, onOpen = onOpen)
            }
        }
    }
}

@Composable
fun LibraryScreen(catalog: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    var filter by remember { mutableStateOf<MediaKind?>(null) }
    val items = remember(catalog, filter) { catalog.filter { filter == null || it.kind == filter } }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterPill("All", filter == null) { filter = null }
            FilterPill("Movies", filter == MediaKind.MOVIE) { filter = MediaKind.MOVIE }
            FilterPill("Series", filter == MediaKind.SERIES) { filter = MediaKind.SERIES }
        }
        Text("${items.size} titles from connected sources", color = OpenStreamColors.Muted, fontSize = 13.sp)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            items(items, key = { it.id }) { item ->
                MediaCard(item, width = 150.dp, onOpen = onOpen)
            }
        }
    }
}

@Composable
fun SearchScreen(catalog: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(catalog, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) catalog
        else catalog.filter { "${it.title} ${it.synopsis}".lowercase().contains(normalized) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            singleLine = true,
            placeholder = { Text("Search titles across every connected source") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        )
        Text(
            "${results.size} matches",
            modifier = Modifier.padding(vertical = 10.dp),
            color = OpenStreamColors.Muted,
            fontSize = 13.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(results, key = { it.id }) { item ->
                MediaCard(item, width = 150.dp, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) OpenStreamColors.SurfaceRaised else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) OpenStreamColors.Cyan.copy(alpha = 0.5f) else OpenStreamColors.Border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) OpenStreamColors.Cyan else OpenStreamColors.TextSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyCatalog() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Nothing to show yet", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
        Text(
            "No titles match this device's content settings. Add age-appropriate media to a connected library.",
            modifier = Modifier.padding(top = 10.dp),
            color = OpenStreamColors.TextSoft,
            fontSize = 15.sp,
        )
    }
}
