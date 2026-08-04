package com.orgista.openstream.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.orgista.openstream.R
import com.orgista.openstream.data.ConnectionStore
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.NasConnection
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
            onStandard = { policy = profileStore.completeStandardOnboarding() },
            onKids = { kidsPolicy -> policy = profileStore.enableKidsMode(kidsPolicy) },
        )
        return
    }

    val kidsMode = activePolicy is ContentPolicy.Kids
    val catalog = remember(activePolicy) { filterCatalog(demoCatalog, activePolicy) }
    var screen by remember { mutableStateOf<Screen>(Screen.Discover) }

    val playGuarded: (MediaItem, PlaybackRequest) -> Unit = { item, request ->
        if (isItemAllowed(item, activePolicy)) {
            screen = Screen.Player(request)
        } else {
            Toast.makeText(context, "Not available in Kids Mode", Toast.LENGTH_SHORT).show()
        }
    }

    when (val current = screen) {
        is Screen.Details -> {
            BackHandler { screen = Screen.Discover }
            DetailsScreen(
                item = current.item,
                kidsMode = kidsMode,
                addons = connectionStore.stremioAddons(),
                onBack = { screen = Screen.Discover },
                onPlay = { request -> playGuarded(current.item, request) },
            )
            return
        }
        is Screen.Player -> {
            BackHandler { screen = Screen.Discover }
            PlayerScreen(request = current.request, onBack = { screen = Screen.Discover })
            return
        }
        else -> Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppHeader(kidsMode) },
        bottomBar = {
            NavigationBar(containerColor = OpenStreamColors.CanvasDeep) {
                NavItem("Discover", AppIcons.Home, screen == Screen.Discover) { screen = Screen.Discover }
                NavItem("Library", AppIcons.Library, screen == Screen.Library) { screen = Screen.Library }
                NavItem("Search", AppIcons.Search, screen == Screen.Search) { screen = Screen.Search }
                if (!kidsMode) {
                    NavItem("Connections", AppIcons.Storage, screen == Screen.Connections) { screen = Screen.Connections }
                }
                NavItem("Settings", AppIcons.Settings, screen == Screen.Settings) { screen = Screen.Settings }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Discover -> DiscoverScreen(
                    catalog = catalog,
                    onOpen = { item -> screen = Screen.Details(item) },
                )
                Screen.Library -> LibraryScreen(
                    catalog = catalog,
                    onOpen = { item -> screen = Screen.Details(item) },
                )
                Screen.Search -> SearchScreen(
                    catalog = catalog,
                    onOpen = { item -> screen = Screen.Details(item) },
                )
                Screen.Connections -> ConnectionsScreen(
                    store = connectionStore,
                    onPlayNas = { connection, entry ->
                        val request = PlaybackRequest(
                            title = entry.name.substringBeforeLast('.'),
                            subtitle = connection.name,
                            uri = com.orgista.openstream.data.Smb.fileUrl(connection, entry.path),
                            nasConnection = connection,
                        )
                        // NAS files carry no certification; Kids Mode blocks them.
                        if (kidsMode) {
                            Toast.makeText(context, "Not available in Kids Mode", Toast.LENGTH_SHORT).show()
                        } else {
                            screen = Screen.Player(request)
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
private fun AppHeader(kidsMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenStreamColors.CanvasDeep)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.openstream_mark),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        Text("OpenStream", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
        Text("by Orgista", fontSize = 13.sp, color = OpenStreamColors.Muted)
        if (kidsMode) {
            Surface(
                shape = RoundedCornerShape(50),
                color = OpenStreamColors.Amber.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, OpenStreamColors.Amber.copy(alpha = 0.45f)),
            ) {
                Text(
                    "KIDS",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OpenStreamColors.Amber,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = OpenStreamColors.Cyan,
            selectedTextColor = OpenStreamColors.Cyan,
            indicatorColor = OpenStreamColors.Surface,
            unselectedIconColor = OpenStreamColors.Muted,
            unselectedTextColor = OpenStreamColors.Muted,
        ),
    )
}
