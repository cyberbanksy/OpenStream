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
    var policy by remember { mutableStateOf(profileStore.loadActivePolicy()) }

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
    val catalog = remember(activePolicy) { filterCatalog(demoCatalog, activePolicy) }
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
            Logs.event("play: ${request.title} (${request.uri.substringBefore("://")})")
            navigate(Screen.Player(request))
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
                Screen.Settings -> SettingsScreen(policy = activePolicy)
                else -> Unit
            }
        }
    }
}

@Composable
private fun TopNav(
    kidsMode: Boolean,
    current: Screen,
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
