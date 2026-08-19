package com.orgista.openstream.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.data.AddonCatalogSection
import com.orgista.openstream.data.DiscoverFeedPreferences
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.domain.CatalogSections
import com.orgista.openstream.domain.FeaturedCatalog
import com.orgista.openstream.domain.TitleSearch
import com.orgista.openstream.ui.icons.AppIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatRuntime(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours == 0) "${rest}m" else "${hours}h ${rest.toString().padStart(2, '0')}m"
}

@Composable
fun DiscoverScreen(
    catalog: List<MediaItem>,
    addonCatalogs: List<AddonCatalogSection> = emptyList(),
    feedPreferences: DiscoverFeedPreferences = DiscoverFeedPreferences.DEFAULT,
    upNextItems: List<MediaItem> = emptyList(),
    offlineMode: Boolean = false,
    onOpen: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    if (catalog.isEmpty()) {
        EmptyCatalog(offlineMode)
        return
    }
    val featuredCatalog = remember(catalog) { catalog.filter { it.kind != MediaKind.VIDEO }.ifEmpty { catalog } }
    val catalogIds = remember(featuredCatalog) { featuredCatalog.map { it.id } }
    var featuredIndex by remember(catalogIds) { mutableIntStateOf(0) }
    val safeIndex = featuredIndex.coerceIn(0, featuredCatalog.lastIndex)
    val featured = featuredCatalog[safeIndex]
    val sections = remember(catalog, addonCatalogs, feedPreferences, upNextItems) {
        CatalogSections.buildDiscover(catalog, addonCatalogs, feedPreferences, upNextItems)
    }
    val configuration = LocalConfiguration.current
    val layout = BrowseLayoutPolicy.resolve(
        widthDp = configuration.screenWidthDp.toFloat(),
        heightDp = configuration.screenHeightDp.toFloat(),
    )
    val compactLandscape = layout.compact && configuration.screenWidthDp > configuration.screenHeightDp
    val heroHeight = layout.featuredHeightDp.dp
    val featuredProgressStyle = BrowseLayoutPolicy.featuredProgressStyle(layout.compact)
    val featuredProgress = remember(catalogIds) { Animatable(0f) }

    LaunchedEffect(catalogIds, featuredIndex) {
        featuredProgress.snapTo(0f)
        if (featuredCatalog.size > 1) {
            launch {
                featuredProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FeaturedCatalog.ROTATION_MILLIS.toInt(),
                        easing = LinearEasing,
                    ),
                )
            }
            delay(FeaturedCatalog.ROTATION_MILLIS)
            featuredIndex = FeaturedCatalog.nextIndex(featuredIndex, featuredCatalog.size)
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "featured") {
            Box(Modifier.fillMaxWidth().height(heroHeight).background(Color.Black)) {
                CinematicArtwork(featured, Modifier.fillMaxSize())
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
                    Modifier
                        .align(if (layout.compact) Alignment.BottomStart else Alignment.CenterStart)
                        .padding(
                            start = layout.horizontalPaddingDp.dp,
                            end = layout.horizontalPaddingDp.dp,
                            top = 20.dp,
                            bottom = when {
                                compactLandscape -> 12.dp
                                layout.compact -> 22.dp
                                else -> 0.dp
                            },
                        )
                        .fillMaxWidth(layout.heroContentFraction),
                    verticalArrangement = Arrangement.spacedBy(
                        when {
                            compactLandscape -> 5.dp
                            layout.compact -> 8.dp
                            else -> 11.dp
                        },
                    ),
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
                        fontSize = layout.heroTitleSp.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OpenStreamColors.Text,
                        lineHeight = (layout.heroTitleSp + 4f).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetaRow(featured, includeFormat = layout.showFormatInHero)
                    if (!compactLandscape) {
                        Text(
                            featured.synopsis,
                            fontSize = 15.sp,
                            color = OpenStreamColors.TextSoft,
                            lineHeight = 22.sp,
                            maxLines = if (layout.compact) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Play", icon = AppIcons.Play) { onPlay(featured) }
                        PillButton("Details", prominent = false) { onOpen(featured) }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = featuredProgressStyle.bottomPaddingDp.dp)
                        .width(featuredProgressStyle.widthDp.dp)
                        .height(featuredProgressStyle.heightDp.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = featuredProgressStyle.trackAlpha)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(featuredProgress.value.coerceIn(0f, 1f))
                            .height(featuredProgressStyle.heightDp.dp)
                            .background(Color.White.copy(alpha = featuredProgressStyle.fillAlpha)),
                    )
                }
            }
        }

        itemsIndexed(
            items = sections,
            key = { index, section -> "section-$index-${section.addonName}-${section.title}" },
        ) { sectionIndex, section ->
            Column {
                val addonName = section.addonName
                val previousAddon = sections.getOrNull(sectionIndex - 1)?.addonName
                if (addonName != null && addonName != previousAddon) {
                    Text(
                        addonName,
                        modifier = Modifier.padding(
                            start = layout.horizontalPaddingDp.dp,
                            top = if (sectionIndex == 0) 10.dp else 30.dp,
                        ),
                        fontSize = if (layout.compact) 20.sp else 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OpenStreamColors.Text,
                    )
                }
                SectionHeader(
                    section.title,
                    Modifier.padding(
                        start = layout.horizontalPaddingDp.dp,
                        top = if (addonName != null) 8.dp else if (sectionIndex == 0) 8.dp else 24.dp,
                        bottom = 12.dp,
                    ),
                )
                MediaRail(section.items, keyPrefix = "section-$sectionIndex-", layout = layout) { selected ->
                    featuredIndex = FeaturedCatalog.indexForSelection(catalogIds, selected.id)
                    onOpen(selected)
                }
            }
        }
        item(key = "bottom-space") { Box(Modifier.height(28.dp)) }
    }
}

@Composable
private fun MediaRail(
    items: List<MediaItem>,
    keyPrefix: String = "",
    layout: BrowseLayout,
    onOpen: (MediaItem) -> Unit,
) {
    val railState = rememberLazyListState()
    LazyRow(
        state = railState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = railState),
        contentPadding = PaddingValues(horizontal = layout.horizontalPaddingDp.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(layout.itemSpacingDp.dp),
    ) {
        items(items, key = { "$keyPrefix${it.id}" }) { item ->
            MediaCard(
                item,
                width = layout.cardWidthDp.dp,
                useBackdrop = item.kind == MediaKind.VIDEO,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
fun LibraryScreen(catalog: List<MediaItem>, onOpen: (MediaItem) -> Unit) {
    var filter by remember { mutableStateOf<MediaKind?>(null) }
    val items = remember(catalog, filter) { catalog.filter { filter == null || it.kind == filter } }
    val configuration = LocalConfiguration.current
    val layout = BrowseLayoutPolicy.resolve(
        widthDp = configuration.screenWidthDp.toFloat(),
        heightDp = configuration.screenHeightDp.toFloat(),
    )

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenStreamColors.Canvas)
            .padding(horizontal = layout.horizontalPaddingDp.dp),
        columns = if (layout.gridColumns > 0) GridCells.Fixed(layout.gridColumns) else GridCells.Adaptive(minSize = 184.dp),
        horizontalArrangement = Arrangement.spacedBy(layout.itemSpacingDp.dp),
        verticalArrangement = Arrangement.spacedBy(layout.itemSpacingDp.dp),
        contentPadding = PaddingValues(bottom = 34.dp),
    ) {
        item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(
                count = items.size,
                compact = layout.compact,
                filter = filter,
                onFilterChanged = { filter = it },
            )
        }
        items(items, key = { it.id }) { item ->
            MediaCard(
                item,
                width = layout.cardWidthDp.dp,
                useBackdrop = item.kind == MediaKind.VIDEO,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    count: Int,
    compact: Boolean,
    filter: MediaKind?,
    onFilterChanged: (MediaKind?) -> Unit,
) {
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp)) {
            Text(
                "Library",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OpenStreamColors.Text,
            )
            Text("$count titles from connected sources", color = OpenStreamColors.Muted, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FilterPill("All", filter == null) { onFilterChanged(null) }
                FilterPill("Movies", filter == MediaKind.MOVIE) { onFilterChanged(MediaKind.MOVIE) }
                FilterPill("Series", filter == MediaKind.SERIES) { onFilterChanged(MediaKind.SERIES) }
            }
        }
    } else {
        Row(
            modifier = Modifier.padding(top = 24.dp, bottom = 18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Library", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
                Text("$count titles from connected sources", color = OpenStreamColors.Muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterPill("All", filter == null) { onFilterChanged(null) }
                FilterPill("Movies", filter == MediaKind.MOVIE) { onFilterChanged(MediaKind.MOVIE) }
                FilterPill("Series", filter == MediaKind.SERIES) { onFilterChanged(MediaKind.SERIES) }
            }
        }
    }
}

@Composable
fun SearchScreen(
    catalog: List<MediaItem>,
    online: Boolean,
    remoteSearch: suspend (String) -> List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var remoteResults by remember { mutableStateOf(emptyList<MediaItem>()) }
    var searching by remember { mutableStateOf(false) }
    val normalizedQuery = query.trim()
    val localSearchIndex = remember(catalog) { TitleSearch.index(catalog) }
    val localResults = remember(localSearchIndex, normalizedQuery) {
        localSearchIndex.rank(normalizedQuery)
    }
    val results = remember(localResults, remoteResults, normalizedQuery) {
        if (normalizedQuery.isEmpty()) catalog
        else TitleSearch.mergeAndRank(localResults, remoteResults, normalizedQuery)
    }

    LaunchedEffect(normalizedQuery, online) {
        remoteResults = emptyList()
        searching = false
        if (!online || normalizedQuery.length < 2) return@LaunchedEffect
        delay(350)
        searching = true
        remoteResults = try {
            remoteSearch(normalizedQuery)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        searching = false
    }
    val configuration = LocalConfiguration.current
    val layout = BrowseLayoutPolicy.resolve(
        widthDp = configuration.screenWidthDp.toFloat(),
        heightDp = configuration.screenHeightDp.toFloat(),
    )

    Column(Modifier.fillMaxSize().padding(horizontal = layout.horizontalPaddingDp.dp)) {
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
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searching) OpenStreamLoadingIndicator(role = LoadingIndicatorRole.INLINE)
            Text(
                when {
                    searching -> "Searching connected catalogs…"
                    normalizedQuery.isNotEmpty() && !online -> "${results.size} offline matches"
                    else -> "${results.size} matches"
                },
                color = OpenStreamColors.Muted,
                fontSize = 13.sp,
            )
        }
        LazyVerticalGrid(
            columns = if (layout.gridColumns > 0) GridCells.Fixed(layout.gridColumns) else GridCells.Adaptive(minSize = 184.dp),
            horizontalArrangement = Arrangement.spacedBy(layout.itemSpacingDp.dp),
            verticalArrangement = Arrangement.spacedBy(layout.itemSpacingDp.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
        ) {
            items(results, key = { it.id }) { item ->
                MediaCard(
                    item,
                    width = layout.cardWidthDp.dp,
                    useBackdrop = item.kind == MediaKind.VIDEO,
                    onOpen = onOpen,
                )
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
private fun EmptyCatalog(offlineMode: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (offlineMode) "No offline downloads yet" else "Nothing to show yet",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OpenStreamColors.Text,
        )
        Text(
            if (offlineMode) {
                "Reconnect, open a title, and choose Make available offline. Completed downloads appear here automatically."
            } else {
                "Add a library, add-on, or YouTube channel in Sources to fill Discover."
            },
            modifier = Modifier.padding(top = 10.dp),
            color = OpenStreamColors.TextSoft,
            fontSize = 15.sp,
        )
    }
}
