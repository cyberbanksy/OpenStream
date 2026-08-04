package com.orgista.openstream.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun DiscoverScreen(catalog: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    if (catalog.isEmpty()) {
        EmptyCatalog()
        return
    }
    var featured by remember(catalog) { mutableStateOf(catalog.first()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(400.dp)) {
            Poster(featured, Modifier.fillMaxSize(), useBackdrop = true)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to OpenStreamColors.Canvas,
                        0.5f to OpenStreamColors.Canvas.copy(alpha = 0.62f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to OpenStreamColors.Canvas,
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = 34.dp, end = 34.dp, top = 20.dp).fillMaxWidth(0.6f),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text(
                    if (featured.kind == MediaKind.SERIES) "SERIES" else "FEATURED",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    color = OpenStreamColors.Accent,
                )
                Text(
                    featured.title,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OpenStreamColors.Text,
                    lineHeight = 50.sp,
                )
                MetaRow(featured)
                Text(
                    featured.synopsis,
                    fontSize = 15.sp,
                    color = OpenStreamColors.TextSoft,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Play", icon = AppIcons.Play) { onOpen(featured) }
                    PillButton("Details", prominent = false) { onOpen(featured) }
                }
            }
        }

        val continueWatching = catalog.filter { it.progressPercent != null }
        if (continueWatching.isNotEmpty()) {
            SectionHeader("Continue watching", Modifier.padding(start = 34.dp, top = 8.dp, bottom = 12.dp))
            MediaRail(continueWatching) { item ->
                featured = item
                onOpen(item)
            }
        }

        SectionHeader("All titles", Modifier.padding(start = 34.dp, top = 24.dp, bottom = 12.dp))
        MediaRail(catalog, keyPrefix = "all-") { item -> onOpen(item) }
        Box(Modifier.height(28.dp))
    }
}

@Composable
private fun MediaRail(items: List<MediaItem>, keyPrefix: String = "", onOpen: (MediaItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { "$keyPrefix${it.id}" }) { item ->
            MediaCard(item, width = 156.dp, onOpen = onOpen)
        }
    }
}

@Composable
fun LibraryScreen(catalog: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    var filter by remember { mutableStateOf<MediaKind?>(null) }
    val items = remember(catalog, filter) { catalog.filter { filter == null || it.kind == filter } }

    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp)) {
        Row(
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Library", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
                Text("${items.size} titles from connected sources", color = OpenStreamColors.Muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterPill("All", filter == null) { filter = null }
                FilterPill("Movies", filter == MediaKind.MOVIE) { filter = MediaKind.MOVIE }
                FilterPill("Series", filter == MediaKind.SERIES) { filter = MediaKind.SERIES }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
        ) {
            items(items, key = { it.id }) { item ->
                MediaCard(item, width = 156.dp, onOpen = onOpen)
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

    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Search titles across every connected source", color = OpenStreamColors.Muted) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = OpenStreamColors.Muted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OpenStreamColors.Accent.copy(alpha = 0.6f),
                unfocusedBorderColor = OpenStreamColors.Border,
            ),
        )
        Text(
            "${results.size} matches",
            modifier = Modifier.padding(vertical = 12.dp),
            color = OpenStreamColors.Muted,
            fontSize = 13.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            items(results, key = { it.id }) { item ->
                MediaCard(item, width = 156.dp, onOpen = onOpen)
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
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
            color = if (selected) OpenStreamColors.Cyan else OpenStreamColors.TextSoft,
            fontSize = 13.sp,
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
