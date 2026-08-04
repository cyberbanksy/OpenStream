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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun OpenStreamApp() {
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
    var lastBrowseScreen by remember { mutableStateOf<Screen>(Screen.Discover) }

    val navigate: (Screen) -> Unit = { next ->
        Logs.event("navigate: ${next::class.simpleName}")
        if (next is Screen.Discover || next is Screen.Library || next is Screen.Search ||
            next is Screen.Connections || next is Screen.Settings
        ) {
            lastBrowseScreen = next
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
            BackHandler { screen = lastBrowseScreen }
            DetailsScreen(
                item = current.item,
                kidsMode = kidsMode,
                addons = connectionStore.stremioAddons(),
                onBack = { screen = lastBrowseScreen },
                onPlay = { request -> playGuarded(current.item, request) },
            )
            return
        }
        is Screen.Player -> {
            BackHandler { screen = lastBrowseScreen }
            PlayerScreen(request = current.request, onBack = { screen = lastBrowseScreen })
            return
        }
        else -> Unit
    }

    // From any tab other than Discover, back returns to Discover; from
    // Discover the system handles it and leaves the app.
    BackHandler(enabled = screen != Screen.Discover) { navigate(Screen.Discover) }

    Row(Modifier.fillMaxSize().background(OpenStreamColors.Canvas)) {
        NavRail(
            kidsMode = kidsMode,
            current = screen,
            onNavigate = navigate,
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
                        // NAS files carry no certification; Kids Mode blocks them.
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
private fun NavRail(
    kidsMode: Boolean,
    current: Screen,
    onNavigate: (Screen) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(92.dp)
            .background(OpenStreamColors.CanvasDeep)
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.openstream_mark),
            contentDescription = "OpenStream",
            modifier = Modifier.size(34.dp),
        )
        if (kidsMode) {
            Surface(
                modifier = Modifier.padding(top = 10.dp),
                shape = RoundedCornerShape(50),
                color = OpenStreamColors.Amber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, OpenStreamColors.Amber.copy(alpha = 0.45f)),
            ) {
                Text(
                    "KIDS",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = OpenStreamColors.Amber,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        RailItem("Discover", AppIcons.Home, current == Screen.Discover) { onNavigate(Screen.Discover) }
        RailItem("Library", AppIcons.Library, current == Screen.Library) { onNavigate(Screen.Library) }
        RailItem("Search", AppIcons.Search, current == Screen.Search) { onNavigate(Screen.Search) }
        if (!kidsMode) {
            RailItem("Sources", AppIcons.Storage, current == Screen.Connections) { onNavigate(Screen.Connections) }
        }
        Spacer(Modifier.weight(1f))
        RailItem("Settings", AppIcons.Settings, current == Screen.Settings) { onNavigate(Screen.Settings) }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) OpenStreamColors.Surface else Color.Transparent,
    ) {
        Column(
            Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) OpenStreamColors.Cyan else OpenStreamColors.Muted,
                modifier = Modifier.size(23.dp),
            )
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) OpenStreamColors.Cyan else OpenStreamColors.Muted,
            )
        }
    }
}
