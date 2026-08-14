package com.orgista.openstream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.iptv.IptvChannel
import com.orgista.openstream.iptv.IptvCatalogPolicy
import com.orgista.openstream.iptv.IptvClient
import com.orgista.openstream.iptv.IptvStore
import com.orgista.openstream.iptv.LiveChannelPackage
import com.orgista.openstream.iptv.LiveChannelPackagePolicy
import com.orgista.openstream.iptv.ManualLiveChannelPolicy
import com.orgista.openstream.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LiveChannelManagerScreen(
    store: IptvStore,
    allChannels: List<IptvChannel>,
    onChanged: () -> Unit,
    onPackagesChanged: () -> Unit,
    onOpenSources: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var catalogChannels by remember(allChannels) { mutableStateOf(allChannels) }
    var loadingCatalog by remember { mutableStateOf(allChannels.isEmpty()) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var enabledPackageIds by remember { mutableStateOf(store.enabledPackageIds()) }
    var favoriteKeys by remember { mutableStateOf(store.favoriteChannelKeys()) }
    var query by remember { mutableStateOf("") }
    var settledQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        delay(110)
        settledQuery = query
    }
    LaunchedEffect(Unit) {
        if (catalogChannels.isNotEmpty()) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            coroutineScope {
                store.sources().map { source ->
                    async { runCatching { IptvClient.loadCatalog(source).channels }.getOrDefault(emptyList()) }
                }.awaitAll().flatten()
            }
        }
        val merged = withContext(Dispatchers.Default) {
            IptvCatalogPolicy.mergeForPlayback(loaded + store.manualChannels())
        }
        catalogChannels = merged
        store.updateSessionChannels(merged)
        loadingCatalog = false
        if (merged.isEmpty()) catalogError = "No channels are available. Check the Live TV source above."
    }
    val packageCounts = remember(catalogChannels) {
        catalogChannels.groupingBy(LiveChannelPackagePolicy::packageFor).eachCount()
    }
    val favorites = remember(catalogChannels, favoriteKeys) {
        catalogChannels.filter {
            LiveChannelPackagePolicy.channelKey(it) in favoriteKeys || it.sourceId == ManualLiveChannelPolicy.SOURCE_ID
        }.distinctBy(LiveChannelPackagePolicy::channelKey).sortedBy { it.name.lowercase() }
    }
    val searchIndex = remember(catalogChannels) {
        LiveChannelPackagePolicy.buildSearchIndex(catalogChannels)
    }
    val searchResults = remember(searchIndex, settledQuery) {
        LiveChannelPackagePolicy.searchIndexedChannels(searchIndex, settledQuery)
    }
    val activeCount = remember(catalogChannels, enabledPackageIds, favoriteKeys) {
        LiveChannelPackagePolicy.lineupChannels(
            channels = catalogChannels,
            enabledPackageIds = enabledPackageIds,
            favoriteChannelKeys = favoriteKeys,
        ).size
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.Back, contentDescription = "Back", tint = OpenStreamColors.Text)
            }
            Column(Modifier.weight(1f)) {
                Text("Channel manager", color = OpenStreamColors.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        loadingCatalog -> "Loading available channels…"
                        else -> "$activeCount on · ${catalogChannels.size} available"
                    },
                    color = OpenStreamColors.Muted,
                    fontSize = 13.sp,
                )
            }
            OutlinedButton(onClick = onOpenSources) { Text("Sources") }
            Button(onClick = { showAddDialog = true }) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Add channel")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 22.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            catalogError?.let { message ->
                item("catalog-error") {
                    Text(message, color = OpenStreamColors.Danger, fontSize = 13.sp)
                }
            }
            item("packages-title") {
                    Text("Channel packages", color = OpenStreamColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            item("packages") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(LiveChannelPackagePolicy.orderedPackages, key = LiveChannelPackage::id) { packageInfo ->
                        FilterChip(
                            selected = packageInfo.id in enabledPackageIds,
                            onClick = {
                                val selected = packageInfo.id !in enabledPackageIds
                                store.setPackageEnabled(packageInfo.id, selected)
                                enabledPackageIds = store.enabledPackageIds()
                                onPackagesChanged()
                            },
                            label = { Text("${packageInfo.label}  ${packageCounts[packageInfo] ?: 0}") },
                        )
                    }
                }
            }
            item("my-title") {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("My Channels", color = OpenStreamColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (favorites.isEmpty()) "Search below to add favorites, or add a stream that is missing."
                        else "${favorites.size} selected channels",
                        color = OpenStreamColors.Muted,
                        fontSize = 13.sp,
                    )
                }
            }
            items(favorites, key = { "favorite-${LiveChannelPackagePolicy.channelKey(it)}" }) { channel ->
                ChannelManagerRow(
                    channel = channel,
                    selected = true,
                    onToggle = {
                        if (channel.sourceId == ManualLiveChannelPolicy.SOURCE_ID) {
                            store.removeManualChannel(channel.id)
                            catalogChannels = catalogChannels.filterNot {
                                it.sourceId == ManualLiveChannelPolicy.SOURCE_ID && it.id == channel.id
                            }
                        } else {
                            store.setFavorite(channel, false)
                        }
                        favoriteKeys = store.favoriteChannelKeys()
                        onChanged()
                    },
                    remove = channel.sourceId == ManualLiveChannelPolicy.SOURCE_ID,
                )
            }
            item("search-title") {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Find channels", color = OpenStreamColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = OpenStreamColors.Muted) },
                        placeholder = { Text("Search every available channel") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                    if (query.trim().length < 2) {
                        Text("Enter at least two letters.", color = OpenStreamColors.Muted, fontSize = 12.sp)
                    } else if (searchResults.isEmpty()) {
                        Text("No matching channel. Add a direct stream with Add channel.", color = OpenStreamColors.TextSoft, fontSize = 13.sp)
                    }
                }
            }
            items(searchResults, key = { "result-${LiveChannelPackagePolicy.channelKey(it)}" }) { channel ->
                val selected = LiveChannelPackagePolicy.channelKey(channel) in favoriteKeys ||
                    channel.sourceId == ManualLiveChannelPolicy.SOURCE_ID
                ChannelManagerRow(
                    channel = channel,
                    selected = selected,
                    onToggle = {
                        store.setFavorite(channel, !selected)
                        favoriteKeys = store.favoriteChannelKeys()
                        onChanged()
                    },
                )
            }
        }
    }

    if (showAddDialog) {
        AddLiveChannelDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, packageInfo ->
                val added = store.addManualChannel(name, url, packageInfo)
                catalogChannels = IptvCatalogPolicy.mergeForPlayback(catalogChannels + added)
                store.updateSessionChannels(catalogChannels)
                favoriteKeys = store.favoriteChannelKeys()
                showAddDialog = false
                onChanged()
            },
        )
    }
}

@Composable
private fun ChannelManagerRow(
    channel: IptvChannel,
    selected: Boolean,
    onToggle: () -> Unit,
    remove: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = OpenStreamColors.Surface,
        border = BorderStroke(1.dp, OpenStreamColors.Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = OpenStreamColors.Text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    LiveChannelPackagePolicy.packageFor(channel).label,
                    color = OpenStreamColors.Muted,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = onToggle) {
                if (selected && !remove) {
                    Icon(AppIcons.Check, contentDescription = null, tint = OpenStreamColors.Green, modifier = Modifier.size(18.dp))
                    Text(" Added")
                } else {
                    Icon(
                        if (remove) AppIcons.Close else AppIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(if (remove) " Remove" else " Add")
                }
            }
        }
    }
}

@Composable
private fun AddLiveChannelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, LiveChannelPackage) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var packageInfo by remember { mutableStateOf(LiveChannelPackage.LOCAL) }
    var packageMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add a direct HTTP or HTTPS stream to My Channels.", color = OpenStreamColors.TextSoft, fontSize = 13.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Channel name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it; error = null },
                    label = { Text("Stream address") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { packageMenuOpen = true }) { Text("Package: ${packageInfo.label}") }
                    DropdownMenu(expanded = packageMenuOpen, onDismissRequest = { packageMenuOpen = false }) {
                        LiveChannelPackagePolicy.orderedPackages.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { packageInfo = option; packageMenuOpen = false },
                            )
                        }
                    }
                }
                error?.let { Text(it, color = OpenStreamColors.Danger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!ManualLiveChannelPolicy.isValid(name, streamUrl)) {
                    error = "Enter a channel name and a valid HTTP or HTTPS stream address."
                } else {
                    onAdd(name, streamUrl, packageInfo)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
