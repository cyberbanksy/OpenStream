package com.orgista.openstream.ui

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.orgista.openstream.Logs
import com.orgista.openstream.data.ConnectionStore
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.NasConnection
import com.orgista.openstream.data.Smb
import com.orgista.openstream.data.StremioCatalogCache
import com.orgista.openstream.data.StremioCatalogMerger
import com.orgista.openstream.data.StremioClient
import com.orgista.openstream.data.demoCatalog
import com.orgista.openstream.domain.ContentPolicy
import com.orgista.openstream.domain.ProfileStore
import com.orgista.openstream.domain.RecommendationEngine
import com.orgista.openstream.domain.filterCatalog
import com.orgista.openstream.domain.isItemAllowed
import com.orgista.openstream.library.IndexedLibrary
import com.orgista.openstream.library.LibraryIndexer
import com.orgista.openstream.library.LibraryKind
import com.orgista.openstream.library.LibraryStore
import com.orgista.openstream.library.Tmdb
import com.orgista.openstream.player.PlaybackStore
import com.orgista.openstream.offline.OfflineCatalogPolicy
import com.orgista.openstream.offline.OfflineMediaRuntime
import com.orgista.openstream.ui.icons.AppIcons
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch

/** A playback request routed to the player: what to play and how to reach it. */
data class PlaybackRequest(
    val title: String,
    val subtitle: String?,
    val uri: String,
    val nasConnection: NasConnection? = null,
    val mediaId: String? = null,
    val fallbackUris: List<String> = emptyList(),
    val offlineDownloadId: String? = null,
)

fun playableUris(request: PlaybackRequest): List<String> =
    (listOf(request.uri) + request.fallbackUris)
        .filter { uri ->
            uri.startsWith("https://") || uri.startsWith("http://") ||
                uri.startsWith("smb://") || uri.startsWith("rawresource://")
        }
        .distinct()
        .take(12)

fun preferredPlaybackRequest(item: MediaItem): PlaybackRequest? {
    val candidate = item.candidates.firstOrNull() ?: return null
    return PlaybackRequest(
        title = item.title,
        subtitle = candidate.sourceName,
        uri = candidate.uri,
        mediaId = item.id,
        offlineDownloadId = candidate.offlineDownloadId,
    )
}

sealed interface Screen {
    data object Discover : Screen
    data object Library : Screen
    data object Search : Screen
    data object Connections : Screen
    data object Settings : Screen
    data class Details(val item: MediaItem) : Screen
    data class Player(val request: PlaybackRequest) : Screen
}

sealed interface BackDestination {
    data class Navigate(val screen: Screen) : BackDestination
    data object Exit : BackDestination
}

fun backDestination(current: Screen, lastTab: Screen): BackDestination = when (current) {
    is Screen.Details, is Screen.Player -> BackDestination.Navigate(lastTab)
    Screen.Discover -> BackDestination.Exit
    else -> BackDestination.Navigate(Screen.Discover)
}

private data class Tab(val screen: Screen, val label: String)

@OptIn(UnstableApi::class)
@Composable
fun OpenStreamApp(onExit: () -> Unit) {
    val context = LocalContext.current
    val profileStore = remember { ProfileStore(context.applicationContext) }
    val connectionStore = remember { ConnectionStore(context.applicationContext) }
    val libraryStore = remember { LibraryStore(context.applicationContext) }
    val playbackStore = remember { PlaybackStore(context.applicationContext) }
    val offlineRuntime = remember { OfflineMediaRuntime.get(context.applicationContext) }
    val offlineEntries by offlineRuntime.entries.collectAsState()
    val deviceStatus = rememberHeaderDeviceStatus()
    val appScope = rememberCoroutineScope()
    var policy by remember { mutableStateOf(profileStore.loadActivePolicy()) }
    // Load the last scan from disk immediately so the real catalog shows on the
    // first frame instead of the demo placeholder; the scan below refreshes it.
    var indexed by remember {
        mutableStateOf(com.orgista.openstream.library.LibraryCache.load(context.applicationContext))
    }
    var indexing by remember { mutableStateOf(false) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var playbackRevision by remember { mutableIntStateOf(0) }
    var addonRevision by remember { mutableIntStateOf(0) }
    val initialAddonCache = remember { StremioCatalogCache.load(context.applicationContext) }
    var addonCatalog by remember { mutableStateOf(initialAddonCache?.items.orEmpty()) }
    val hasLibraries = remember(libraryRevision) { libraryStore.folders().isNotEmpty() }

    // Scan every added folder library, parse filenames, and (if a TMDB key is
    // set) enrich with posters — off the main thread. Re-runs when a folder is
    // added/removed or the key changes (libraryRevision bumps).
    LaunchedEffect(libraryRevision, deviceStatus.online) {
        val folders = libraryStore.folders()
        if (folders.isEmpty()) {
            indexed = null
            return@LaunchedEffect
        }
        // Keep the last successful library snapshot while offline. Attempting
        // an SMB scan with no physical network would otherwise turn a healthy
        // cached library into an empty result.
        if (!deviceStatus.online) return@LaunchedEffect
        indexing = true
        val tmdb = Tmdb(libraryStore.reusableTmdbCredential())
        val connections = connectionStore.nasConnections().associateBy { it.id }
        val movies = ArrayList<MediaItem>()
        val shows = ArrayList<MediaItem>()
        for (folder in folders) {
            val connection = connections[folder.connectionId] ?: continue
            val result = LibraryIndexer.index(connection, folder, tmdb)
            movies += result.movies
            shows += result.shows
        }
        val result = IndexedLibrary(
            movies.sortedBy { it.title.lowercase() },
            shows.sortedBy { it.title.lowercase() },
        )
        indexed = result
        com.orgista.openstream.library.LibraryCache.save(context.applicationContext, result)
        indexing = false
    }

    // Public discovery catalogs refresh every six hours and fall back to the
    // last app-private cache when a provider is offline. A Sources change
    // restarts this loop immediately.
    LaunchedEffect(addonRevision, deviceStatus.online) {
        if (!deviceStatus.online) return@LaunchedEffect
        var firstPass = true
        while (true) {
            val cached = StremioCatalogCache.load(context.applicationContext)
            val shouldRefresh = addonRevision > 0 || !firstPass || cached?.isFresh() != true
            if (shouldRefresh) {
                val activeCatalogAddons = connectionStore.stremioAddons().filter {
                    it.enabled && "catalog" in it.resources
                }
                val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    activeCatalogAddons.flatMap { addon ->
                        runCatching { StremioClient.catalogsFor(addon) }
                            .onFailure { Logs.warn("catalog refresh failed for ${addon.name}") }
                            .getOrDefault(emptyList())
                    }
                }
                if (fetched.isNotEmpty()) {
                    val merged = StremioCatalogMerger.merge(emptyList(), fetched)
                    addonCatalog = merged
                    StremioCatalogCache.save(context.applicationContext, merged)
                    Logs.event("add-on catalog refreshed: ${merged.size} titles")
                } else if (activeCatalogAddons.isEmpty()) {
                    addonCatalog = emptyList()
                } else if (cached != null) {
                    addonCatalog = cached.items
                }
            }
            firstPass = false
            kotlinx.coroutines.delay(6L * 60L * 60L * 1_000L)
        }
    }

    val activePolicy = policy
    if (activePolicy == null) {
        OnboardingScreen(
            onStandard = {
                Logs.event("onboarding completed: standard")
                policy = profileStore.completeStandardOnboarding()
            },
            onKids = { kidsPolicy ->
                Logs.event("onboarding completed: kids (${kidsPolicy.cap.name}, ${kidsPolicy.blockedTerms.size} blocked terms)")
                policy = profileStore.enableKidsMode(kidsPolicy)
            },
        )
        return
    }

    val kidsMode = activePolicy is ContentPolicy.Kids
    // Real library content when folders exist (from cache instantly, then the
    // fresh scan); the demo catalog only when no libraries are configured.
    val library = indexed
    val onlineCatalog = remember(activePolicy, library, hasLibraries, addonCatalog) {
        val local = when {
            library != null && !library.isEmpty -> library.all
            hasLibraries -> emptyList() // configured but first scan not done yet
            else -> demoCatalog
        }
        StremioCatalogMerger.merge(local, addonCatalog)
    }
    val offlineItems = remember(offlineEntries) {
        offlineEntries.filter { it.completed }.map { it.item }
    }
    val catalog = remember(
        activePolicy,
        onlineCatalog,
        offlineItems,
        deviceStatus.online,
        playbackRevision,
    ) {
        val visible = OfflineCatalogPolicy.select(deviceStatus.online, onlineCatalog, offlineItems)
        val withProgress = visible.map { item ->
            val uri = item.candidates.firstOrNull()?.uri
            val savedProgress = playbackStore.progressPercent(item.id)
                ?: uri?.let(playbackStore::progressPercent)
            if (savedProgress == null) item else item.copy(progressPercent = savedProgress)
        }
        val allowed = filterCatalog(withProgress, activePolicy)
        RecommendationEngine.rank(
            items = allowed,
            history = playbackStore.viewingSignals(allowed.map { it.id }),
        )
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Discover) }
    var lastTab by remember { mutableStateOf<Screen>(Screen.Discover) }

    val navigate: (Screen) -> Unit = { next ->
        Logs.event("navigate: ${next::class.simpleName}")
        if (next is Screen.Discover || next is Screen.Library || next is Screen.Search ||
            next is Screen.Connections || next is Screen.Settings
        ) {
            lastTab = next
        }
        screen = next
    }

    val playGuarded: (MediaItem, PlaybackRequest) -> Unit = { item, request ->
        if (isItemAllowed(item, activePolicy)) {
            // SMB playback needs the connection's credentials. Indexed library
            // items carry only the smb:// URI, so resolve the matching NAS
            // connection by host/share here before handing off to the player.
            var resolved = if (request.uri.startsWith("smb://") && request.nasConnection == null) {
                val connection = connectionStore.nasConnections().firstOrNull { conn ->
                    request.uri.startsWith(Smb.shareUrl(conn))
                }
                request.copy(nasConnection = connection)
            } else {
                request
            }
            if (resolved.mediaId == null) resolved = resolved.copy(mediaId = item.id)
            Logs.event("play: ${resolved.title} (${resolved.uri.substringBefore(':')}, creds=${resolved.nasConnection != null})")
            navigate(Screen.Player(resolved))
        } else {
            Logs.event("play blocked by kids policy: ${item.title}")
            Toast.makeText(context, "Not available in Kids Mode", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        when (val destination = backDestination(screen, lastTab)) {
            is BackDestination.Navigate -> navigate(destination.screen)
            BackDestination.Exit -> {
                Logs.event("exit to launcher (back from Discover)")
                onExit()
            }
        }
    }

    when (val current = screen) {
        is Screen.Details -> {
            DetailsScreen(
                item = current.item,
                kidsMode = kidsMode,
                addons = if (deviceStatus.online) {
                    connectionStore.stremioAddons().filter { it.enabled }
                } else {
                    emptyList()
                },
                offlineRuntime = offlineRuntime,
                online = deviceStatus.online,
                metadataLoader = com.orgista.openstream.library.MetadataLoader(
                    libraryStore.reusableTmdbCredential(),
                    libraryStore.reusableOmdbCredential(),
                ),
                onMetadataEnriched = { enriched ->
                    indexed?.let { currentLibrary ->
                        val updated = currentLibrary.copy(
                            movies = currentLibrary.movies.map { if (it.id == enriched.id) enriched else it },
                            shows = currentLibrary.shows.map { if (it.id == enriched.id) enriched else it },
                        )
                        indexed = updated
                        com.orgista.openstream.library.LibraryCache.save(context.applicationContext, updated)
                    }
                },
                onBack = { screen = lastTab },
                onPlay = { request -> playGuarded(current.item, request) },
            )
            return
        }
        is Screen.Player -> {
            PlayerScreen(
                request = current.request,
                autoSubtitles = libraryStore.autoSubtitles,
                playbackStore = playbackStore,
                onBack = { screen = lastTab },
                onExit = onExit,
                onProgressSaved = { playbackRevision++ },
            )
            return
        }
        else -> Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(OpenStreamColors.Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopNav(
            deviceStatus = deviceStatus,
            kidsMode = kidsMode,
            current = screen,
            indexing = indexing,
            hasLibraries = hasLibraries,
            titleCount = catalog.size,
            metadataEnabled = libraryStore.reusableTmdbCredential().isNotBlank(),
            onNavigate = navigate,
            onRescan = { libraryRevision++ },
            onExit = onExit,
        )
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.Discover -> DiscoverScreen(
                    catalog = catalog,
                    offlineMode = !deviceStatus.online,
                    onOpen = { navigate(Screen.Details(it)) },
                    onPlay = { item ->
                        val request = preferredPlaybackRequest(item)
                        if (request != null) {
                            playGuarded(item, request)
                        } else {
                            val streamAddons = connectionStore.stremioAddons().filter {
                                it.enabled && "stream" in it.resources
                            }
                            val imdbId = item.imdbId
                            if (!kidsMode && imdbId != null && streamAddons.isNotEmpty()) {
                                appScope.launch {
                                    val type = if (item.kind == com.orgista.openstream.data.MediaKind.SERIES) "series" else "movie"
                                    val streams = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        streamAddons.asSequence().flatMap { addon ->
                                            runCatching { StremioClient.streamsFor(addon, type, imdbId) }
                                                .getOrDefault(emptyList())
                                                .asSequence()
                                        }.distinctBy { it.url }.take(12).toList()
                                    }
                                    val stream = streams.firstOrNull()
                                    if (stream == null) {
                                        Toast.makeText(context, "No directly playable stream found", Toast.LENGTH_SHORT).show()
                                        navigate(Screen.Details(item))
                                    } else {
                                        playGuarded(
                                            item,
                                            PlaybackRequest(
                                                item.title,
                                                stream.title,
                                                stream.url,
                                                mediaId = item.id,
                                                fallbackUris = streams.drop(1).map { it.url },
                                            ),
                                        )
                                    }
                                }
                            } else {
                                navigate(Screen.Details(item))
                            }
                        }
                    },
                )
                Screen.Library -> LibraryScreen(catalog) { navigate(Screen.Details(it)) }
                Screen.Search -> SearchScreen(catalog) { navigate(Screen.Details(it)) }
                Screen.Connections -> ConnectionsScreen(
                    store = connectionStore,
                    libraryStore = libraryStore,
                    indexing = indexing,
                    onAddLibrary = { connection, path, kind ->
                        libraryStore.addFolder(connection.id, connection.name, path, kind)
                        Logs.event("library added: ${connection.host}/${connection.share}/$path ($kind)")
                        libraryRevision++
                        navigate(Screen.Discover)
                    },
                    onRemoveLibrary = { id ->
                        libraryStore.removeFolder(id)
                        if (libraryStore.folders().isEmpty()) {
                            com.orgista.openstream.library.LibraryCache.clear(context.applicationContext)
                            indexed = null
                        }
                        libraryRevision++
                    },
                    onPlayNas = { connection, entry ->
                        val request = PlaybackRequest(
                            title = entry.name.substringBeforeLast('.'),
                            subtitle = connection.name,
                            uri = Smb.fileUrl(connection, entry.path),
                            nasConnection = connection,
                        )
                        if (kidsMode) {
                            Toast.makeText(context, "Not available in Kids Mode", Toast.LENGTH_SHORT).show()
                        } else {
                            Logs.event("play nas: ${connection.host}/${connection.share} → ${entry.name}")
                            navigate(Screen.Player(request))
                        }
                    },
                    onAddonsChanged = { addonRevision++ },
                )
                Screen.Settings -> SettingsScreen(
                    policy = activePolicy,
                    libraryStore = libraryStore,
                    onTmdbKeyChanged = { libraryRevision++ },
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun TopNav(
    deviceStatus: HeaderDeviceStatus,
    kidsMode: Boolean,
    current: Screen,
    indexing: Boolean,
    hasLibraries: Boolean,
    titleCount: Int,
    metadataEnabled: Boolean,
    onNavigate: (Screen) -> Unit,
    onRescan: () -> Unit,
    onExit: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val primaryTabs = primaryTopDestinations().map { destination ->
        when (destination) {
            TopDestination.DISCOVER -> Tab(Screen.Discover, "Discover")
            TopDestination.LIBRARY -> Tab(Screen.Library, "Library")
            else -> error("$destination is not a primary tab")
        }
    }
    val headerStatus = libraryHeaderStatus(indexing, hasLibraries, titleCount, metadataEnabled)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenStreamColors.CanvasDeep)
            .padding(horizontal = 26.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("OpenStream", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
        if (kidsMode) {
            Spacer(Modifier.width(10.dp))
            KidsBadge()
        }
        if (!deviceStatus.online) {
            Spacer(Modifier.width(10.dp))
            OfflineBadge()
        }
        Spacer(Modifier.width(38.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            primaryTabs.forEach { tab ->
                TabButton(tab.label, current == tab.screen) { onNavigate(tab.screen) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (indexing) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = OpenStreamColors.Accent,
            )
            Spacer(Modifier.width(14.dp))
        }
        Text(deviceStatus.time, color = OpenStreamColors.TextSoft, fontSize = 13.sp)
        Spacer(Modifier.width(14.dp))
        Icon(
            AppIcons.Wifi,
            contentDescription = if (deviceStatus.wifiConnected) "Wi-Fi connected" else "Wi-Fi disconnected",
            tint = if (deviceStatus.wifiConnected) OpenStreamColors.TextSoft else OpenStreamColors.Muted.copy(alpha = 0.45f),
            modifier = Modifier.size(17.dp),
        )
        if (deviceStatus.batteryPercent >= 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "${deviceStatus.batteryPercent}%",
                color = OpenStreamColors.TextSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = { onNavigate(Screen.Search) }, modifier = Modifier.size(36.dp)) {
            Icon(
                AppIcons.Search,
                contentDescription = "Search",
                tint = if (current == Screen.Search) OpenStreamColors.Accent else OpenStreamColors.TextSoft,
                modifier = Modifier.size(19.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    AppIcons.More,
                    contentDescription = "More options",
                    tint = if (current == Screen.Connections || current == Screen.Settings) {
                        OpenStreamColors.Accent
                    } else {
                        OpenStreamColors.TextSoft
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = OpenStreamColors.SurfaceRaised,
            ) {
                Text(
                    headerStatus,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (indexing) OpenStreamColors.Accent else OpenStreamColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                HorizontalDivider(color = OpenStreamColors.Border)
                overflowTopDestinations(kidsMode).forEach { destination ->
                    val screen = when (destination) {
                        TopDestination.SOURCES -> Screen.Connections
                        TopDestination.SETTINGS -> Screen.Settings
                        else -> error("$destination is not an overflow destination")
                    }
                    val label = if (destination == TopDestination.SOURCES) "Sources" else "Settings"
                    val icon = if (destination == TopDestination.SOURCES) AppIcons.Storage else AppIcons.Settings
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                color = if (current == screen) OpenStreamColors.Accent else OpenStreamColors.Text,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (current == screen) OpenStreamColors.Accent else OpenStreamColors.TextSoft,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onNavigate(screen)
                        },
                    )
                }
                if (hasLibraries) {
                    DropdownMenuItem(
                        text = { Text("Re-scan library", color = OpenStreamColors.Text) },
                        leadingIcon = {
                            Icon(
                                AppIcons.Refresh,
                                contentDescription = null,
                                tint = OpenStreamColors.TextSoft,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        enabled = !indexing,
                        onClick = {
                            menuOpen = false
                            onRescan()
                        },
                    )
                }
                HorizontalDivider(color = OpenStreamColors.Border)
                DropdownMenuItem(
                    text = { Text("Return to OpenPanel", color = OpenStreamColors.Text) },
                    leadingIcon = {
                        Icon(
                            AppIcons.Home,
                            contentDescription = null,
                            tint = OpenStreamColors.TextSoft,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onExit()
                    },
                )
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                label,
                color = if (selected) OpenStreamColors.Text else OpenStreamColors.Muted,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            Box(
                Modifier
                    .width(22.dp)
                    .height(2.dp)
                    .background(if (selected) OpenStreamColors.Accent else Color.Transparent),
            )
        }
    }
}

@Composable
fun KidsBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = OpenStreamColors.AccentSoft,
        border = BorderStroke(1.dp, OpenStreamColors.Accent.copy(alpha = 0.5f)),
    ) {
        Text(
            "KIDS",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = OpenStreamColors.Accent,
        )
    }
}

@Composable
private fun OfflineBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = OpenStreamColors.SurfaceRaised,
        border = BorderStroke(1.dp, OpenStreamColors.Cyan.copy(alpha = 0.55f)),
    ) {
        Text(
            "OFFLINE",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = OpenStreamColors.Cyan,
        )
    }
}
