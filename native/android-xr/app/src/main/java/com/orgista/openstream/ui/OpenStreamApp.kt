package com.orgista.openstream.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.orgista.openstream.Logs
import com.orgista.openstream.R
import com.orgista.openstream.data.ConnectionStore
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.NasConnection
import com.orgista.openstream.data.Smb
import com.orgista.openstream.data.demoCatalog
import com.orgista.openstream.domain.ContentPolicy
import com.orgista.openstream.domain.ProfileStore
import com.orgista.openstream.domain.filterCatalog
import com.orgista.openstream.domain.isItemAllowed
import com.orgista.openstream.library.IndexedLibrary
import com.orgista.openstream.library.LibraryIndexer
import com.orgista.openstream.library.LibraryKind
import com.orgista.openstream.library.LibraryStore
import com.orgista.openstream.library.Tmdb
import com.orgista.openstream.ui.icons.AppIcons

/** A playback request routed to the player: what to play and how to reach it. */
data class PlaybackRequest(
    val title: String,
    val subtitle: String?,
    val uri: String,
    val nasConnection: NasConnection? = null,
)

sealed interface Screen {
    data object Discover : Screen
    data object Library : Screen
    data object Search : Screen
    data object Connections : Screen
    data object Settings : Screen
    data class Details(val item: MediaItem) : Screen
    data class Player(val request: PlaybackRequest) : Screen
}

private data class Tab(val screen: Screen, val label: String, val kidsHidden: Boolean = false)

@Composable
fun OpenStreamApp(onExit: () -> Unit) {
    val context = LocalContext.current
    val profileStore = remember { ProfileStore(context.applicationContext) }
    val connectionStore = remember { ConnectionStore(context.applicationContext) }
    val libraryStore = remember { LibraryStore(context.applicationContext) }
    var policy by remember { mutableStateOf(profileStore.loadActivePolicy()) }
    // Load the last scan from disk immediately so the real catalog shows on the
    // first frame instead of the demo placeholder; the scan below refreshes it.
    var indexed by remember {
        mutableStateOf(com.orgista.openstream.library.LibraryCache.load(context.applicationContext))
    }
    var indexing by remember { mutableStateOf(false) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    val hasLibraries = remember(libraryRevision) { libraryStore.folders().isNotEmpty() }

    // Scan every added folder library, parse filenames, and (if a TMDB key is
    // set) enrich with posters — off the main thread. Re-runs when a folder is
    // added/removed or the key changes (libraryRevision bumps).
    LaunchedEffect(libraryRevision) {
        val folders = libraryStore.folders()
        if (folders.isEmpty()) {
            indexed = null
            return@LaunchedEffect
        }
        indexing = true
        val tmdb = Tmdb(libraryStore.tmdbApiKey)
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
    val catalog = remember(activePolicy, library, hasLibraries) {
        val base = when {
            library != null && !library.isEmpty -> library.all
            hasLibraries -> emptyList() // configured but first scan not done yet
            else -> demoCatalog
        }
        filterCatalog(base, activePolicy)
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
            val resolved = if (request.uri.startsWith("smb://") && request.nasConnection == null) {
                val connection = connectionStore.nasConnections().firstOrNull { conn ->
                    request.uri.startsWith(Smb.shareUrl(conn))
                }
                request.copy(nasConnection = connection)
            } else {
                request
            }
            Logs.event("play: ${resolved.title} (${resolved.uri.substringBefore("://")}, creds=${resolved.nasConnection != null})")
            navigate(Screen.Player(resolved))
        } else {
            Logs.event("play blocked by kids policy: ${item.title}")
            Toast.makeText(context, "Not available in Kids Mode", Toast.LENGTH_SHORT).show()
        }
    }

    when (val current = screen) {
        is Screen.Details -> {
            BackHandler { screen = lastTab }
            DetailsScreen(
                item = current.item,
                kidsMode = kidsMode,
                addons = connectionStore.stremioAddons(),
                metadataLoader = com.orgista.openstream.library.MetadataLoader(
                    libraryStore.tmdbApiKey,
                    libraryStore.omdbApiKey,
                ),
                onBack = { screen = lastTab },
                onPlay = { request -> playGuarded(current.item, request) },
            )
            return
        }
        is Screen.Player -> {
            BackHandler { screen = lastTab }
            PlayerScreen(request = current.request, onBack = { screen = lastTab })
            return
        }
        else -> Unit
    }

    // Back from any tab returns to Discover; from Discover it leaves the app
    // (returning to OpenPanel in the kiosk).
    BackHandler { if (screen != Screen.Discover) navigate(Screen.Discover) else onExit() }

    Column(
        Modifier
            .fillMaxSize()
            .background(OpenStreamColors.Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopNav(
            kidsMode = kidsMode,
            current = screen,
            indexing = indexing,
            onNavigate = navigate,
            onExit = {
                Logs.event("exit to OpenPanel")
                onExit()
            },
        )
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.Discover -> DiscoverScreen(catalog) { navigate(Screen.Details(it)) }
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
    kidsMode: Boolean,
    current: Screen,
    indexing: Boolean,
    onNavigate: (Screen) -> Unit,
    onExit: () -> Unit,
) {
    val tabs = listOf(
        Tab(Screen.Discover, "Discover"),
        Tab(Screen.Library, "Library"),
        Tab(Screen.Search, "Search"),
        Tab(Screen.Connections, "Sources", kidsHidden = true),
        Tab(Screen.Settings, "Settings"),
    ).filter { !kidsMode || !it.kidsHidden }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenStreamColors.CanvasDeep)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.openstream_mark),
            contentDescription = "OpenStream",
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text("OpenStream", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
        if (kidsMode) {
            Spacer(Modifier.width(10.dp))
            KidsBadge()
        }
        Spacer(Modifier.width(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab ->
                TabButton(tab.label, current == tab.screen) { onNavigate(tab.screen) }
            }
        }
        Spacer(Modifier.weight(1f))
        if (indexing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = OpenStreamColors.Accent,
                )
                Text("Scanning library…", color = OpenStreamColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(16.dp))
            }
        }
        Surface(
            onClick = onExit,
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            border = BorderStroke(1.dp, OpenStreamColors.BorderStrong),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(AppIcons.Close, contentDescription = null, tint = OpenStreamColors.TextSoft, modifier = Modifier.size(17.dp))
                Text("Exit", color = OpenStreamColors.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) OpenStreamColors.SurfaceRaised else Color.Transparent,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = if (selected) OpenStreamColors.Text else OpenStreamColors.Muted,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
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
