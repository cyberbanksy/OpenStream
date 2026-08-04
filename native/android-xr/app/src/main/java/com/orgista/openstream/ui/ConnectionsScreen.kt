package com.orgista.openstream.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.orgista.openstream.Logs
import com.orgista.openstream.data.ConnectionStore
import com.orgista.openstream.data.DiscoveredNas
import com.orgista.openstream.data.NasConnection
import com.orgista.openstream.data.NasDiscovery
import com.orgista.openstream.data.NasEntry
import com.orgista.openstream.data.Smb
import com.orgista.openstream.data.StremioClient
import com.orgista.openstream.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes > 0 -> "${bytes / 1_000} KB"
    else -> ""
}

/**
 * Sources management: SMB NAS shares (browsed and played directly, no
 * gateway) and Stremio add-ons. Hidden entirely in Kids Mode.
 */
@Composable
fun ConnectionsScreen(
    store: ConnectionStore,
    onPlayNas: (NasConnection, NasEntry) -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    val nasConnections = remember(revision) { store.nasConnections() }
    val addons = remember(revision) { store.stremioAddons() }
    var browsing by remember { mutableStateOf<NasConnection?>(null) }
    var showAddNas by remember { mutableStateOf(false) }
    var showAddAddon by remember { mutableStateOf(false) }

    val active = browsing
    if (active != null) {
        NasBrowser(connection = active, onBack = { browsing = null }, onPlay = { entry -> onPlayNas(active, entry) })
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Sources", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
                    Text("NAS shares stream directly over SMB; add-ons resolve HTTPS streams.", fontSize = 13.sp, color = OpenStreamColors.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showAddAddon = true }) { Text("+ Add-on") }
                    Button(onClick = { showAddNas = true }) { Text("+ NAS share") }
                }
            }
        }

        item { SectionLabel("NAS shares") }
        if (nasConnections.isEmpty()) {
            item { Text("No NAS shares yet. Add your server's host and share name.", color = OpenStreamColors.Muted, fontSize = 14.sp) }
        }
        items(nasConnections, key = { it.id }) { connection ->
            Surface(
                onClick = { browsing = connection },
                shape = RoundedCornerShape(10.dp),
                color = OpenStreamColors.Surface,
                border = BorderStroke(1.dp, OpenStreamColors.Border),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Storage, contentDescription = null, tint = OpenStreamColors.Cyan)
                    Column(Modifier.weight(1f)) {
                        Text(connection.name, fontWeight = FontWeight.Bold, color = OpenStreamColors.Text)
                        Text("smb://${connection.host}/${connection.share}", fontSize = 13.sp, color = OpenStreamColors.Muted)
                    }
                    IconButton(onClick = {
                        store.removeNas(connection.id)
                        revision++
                    }) { Icon(AppIcons.Close, contentDescription = "Remove", tint = OpenStreamColors.Muted) }
                }
            }
        }

        item { SectionLabel("Stremio add-ons") }
        if (addons.isEmpty()) {
            item { Text("No add-ons yet. Paste an add-on manifest URL to connect one.", color = OpenStreamColors.Muted, fontSize = 14.sp) }
        }
        items(addons, key = { it.id }) { addon ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OpenStreamColors.Surface,
                border = BorderStroke(1.dp, OpenStreamColors.Border),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Check, contentDescription = null, tint = OpenStreamColors.Green)
                    Column(Modifier.weight(1f)) {
                        Text(addon.name, fontWeight = FontWeight.Bold, color = OpenStreamColors.Text)
                        Text("v${addon.version} · ${addon.transportUrl}", fontSize = 13.sp, color = OpenStreamColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        store.removeStremio(addon.id)
                        revision++
                    }) { Icon(AppIcons.Close, contentDescription = "Remove", tint = OpenStreamColors.Muted) }
                }
            }
        }
    }

    if (showAddNas) {
        AddNasDialog(
            store = store,
            onDone = { added ->
                showAddNas = false
                if (added) revision++
            },
        )
    }
    if (showAddAddon) {
        AddAddonDialog(
            store = store,
            onDone = { added ->
                showAddAddon = false
                if (added) revision++
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 10.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = OpenStreamColors.Muted,
    )
}

@Composable
private fun AddNasDialog(store: ConnectionStore, onDone: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var listingShares by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredNas>?>(null) }
    var shares by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!checking) onDone(false) },
        title = { Text("Add a NAS share") },
        text = {
          Column {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            scanning = true
                            error = null
                            scope.launch {
                                discovered = NasDiscovery.discover(context)
                                scanning = false
                                if (discovered.isNullOrEmpty()) {
                                    error = "No SMB servers found on this network. Make sure the tablet and NAS share the same Wi-Fi, then enter the host below."
                                }
                            }
                        },
                        enabled = !scanning,
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Text("  Scanning network…")
                        } else {
                            Text("Scan for servers")
                        }
                    }
                }
                val found = discovered
                if (!found.isNullOrEmpty()) {
                    found.forEach { server ->
                        Surface(
                            onClick = {
                                host = server.host
                                if (name.isBlank()) name = server.name
                                shares = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (host == server.host) OpenStreamColors.SurfaceRaised else OpenStreamColors.Surface,
                            border = BorderStroke(1.dp, if (host == server.host) OpenStreamColors.Cyan.copy(alpha = 0.5f) else OpenStreamColors.Border),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Storage, contentDescription = null, tint = OpenStreamColors.Cyan, modifier = Modifier.size(17.dp))
                                Text(server.name, fontWeight = FontWeight.Bold, color = OpenStreamColors.Text, fontSize = 14.sp)
                                Text(server.host, color = OpenStreamColors.Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim(); shares = null },
                    singleLine = true,
                    label = { Text("Host or IP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                )
                OutlinedTextField(value = username, onValueChange = { username = it.trim(); shares = null }, singleLine = true, label = { Text("Username (blank = guest)") })
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; shares = null },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            listingShares = true
                            error = null
                            scope.launch {
                                val result = runCatching { NasDiscovery.listShares(host, username, password) }
                                listingShares = false
                                result.fold(
                                    onSuccess = { shares = it; if (it.isEmpty()) error = "The server exposed no browseable shares." },
                                    onFailure = { error = it.message ?: "The shares could not be listed." },
                                )
                            }
                        },
                        enabled = !listingShares && host.isNotBlank(),
                    ) {
                        if (listingShares) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Text("  Listing shares…")
                        } else {
                            Text("List shares")
                        }
                    }
                }
                val shareOptions = shares
                if (!shareOptions.isNullOrEmpty()) {
                    shareOptions.forEach { option ->
                        Surface(
                            onClick = { share = option },
                            shape = RoundedCornerShape(8.dp),
                            color = if (share == option) OpenStreamColors.SurfaceRaised else OpenStreamColors.Surface,
                            border = BorderStroke(1.dp, if (share == option) OpenStreamColors.Cyan.copy(alpha = 0.5f) else OpenStreamColors.Border),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Folder, contentDescription = null, tint = OpenStreamColors.Muted, modifier = Modifier.size(16.dp))
                                Text(option, color = OpenStreamColors.Text, fontSize = 14.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(value = share, onValueChange = { share = it.trim() }, singleLine = true, label = { Text("Share name") })
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Display name (optional)") })
            }
            if (error != null) {
                Text(error!!, color = OpenStreamColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
          }
        },
        confirmButton = {
            Button(
                onClick = {
                    checking = true
                    error = null
                    scope.launch {
                        val candidate = NasConnection("pending", name.ifBlank { share }, host, share, username, password)
                        val failure = withContext(Dispatchers.IO) {
                            runCatching { Smb.validate(candidate) }.exceptionOrNull()
                        }
                        checking = false
                        if (failure == null) {
                            store.addNas(name, host, share, username, password)
                            Logs.event("nas connected: $host/$share")
                            onDone(true)
                        } else {
                            Logs.warn("nas connect failed for $host/$share", failure)
                            error = failure.message ?: "The share could not be reached."
                        }
                    }
                },
                enabled = !checking && host.isNotBlank() && share.isNotBlank(),
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Checking…")
                } else {
                    Text("Test & connect")
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (!checking) onDone(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun AddAddonDialog(store: ConnectionStore, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!checking) onDone(false) },
        title = { Text("Add a Stremio add-on") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    singleLine = true,
                    label = { Text("Manifest URL") },
                    placeholder = { Text("https://addon.example/manifest.json") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                )
                if (error != null) Text(error!!, color = OpenStreamColors.Danger, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    checking = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { StremioClient.loadAddon(url) }
                        }
                        checking = false
                        result.fold(
                            onSuccess = { addon ->
                                store.addStremio(addon)
                                onDone(true)
                            },
                            onFailure = { failure ->
                                error = failure.message ?: "The add-on could not be validated."
                            },
                        )
                    }
                },
                enabled = !checking && url.isNotBlank(),
            ) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  Checking…")
                } else {
                    Text("Validate & add")
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (!checking) onDone(false) }) { Text("Cancel") } },
    )
}

@Composable
private fun NasBrowser(connection: NasConnection, onBack: () -> Unit, onPlay: (NasEntry) -> Unit) {
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<NasEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connection.id, path) {
        entries = null
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { Smb.list(connection, path) }
        }
        result.fold(
            onSuccess = { entries = it },
            onFailure = { error = it.message ?: "The folder could not be read." },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (path.isEmpty()) onBack() else path = path.substringBeforeLast('/', "")
            }) { Icon(AppIcons.Back, contentDescription = "Back", tint = OpenStreamColors.Text) }
            Column {
                Text(connection.name, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = OpenStreamColors.Text)
                Text("/${path}", fontSize = 13.sp, color = OpenStreamColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        when {
            error != null -> Text(error!!, color = OpenStreamColors.Danger)
            entries == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Reading folder…", color = OpenStreamColors.Muted)
            }
            entries!!.isEmpty() -> Text("No folders or video files here.", color = OpenStreamColors.Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                items(entries!!, key = { it.path }) { entry ->
                    Surface(
                        onClick = { if (entry.isDirectory) path = entry.path else onPlay(entry) },
                        shape = RoundedCornerShape(8.dp),
                        color = OpenStreamColors.Surface,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (entry.isDirectory) AppIcons.Folder else AppIcons.Play,
                                contentDescription = null,
                                tint = if (entry.isDirectory) OpenStreamColors.Muted else OpenStreamColors.Cyan,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                entry.name,
                                Modifier.weight(1f),
                                color = OpenStreamColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!entry.isDirectory) Text(formatSize(entry.sizeBytes), fontSize = 12.sp, color = OpenStreamColors.Muted)
                        }
                    }
                }
            }
        }
    }
}
