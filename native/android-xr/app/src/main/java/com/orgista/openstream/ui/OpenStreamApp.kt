package com.orgista.openstream.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.orgista.openstream.data.ConfiguredStremioCatalogPolicy
import com.orgista.openstream.data.AddonCatalogSection
import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.NasConnection
import com.orgista.openstream.data.Smb
import com.orgista.openstream.data.SourceKind
import com.orgista.openstream.data.StremioStream
import com.orgista.openstream.data.StremioCatalogCache
import com.orgista.openstream.data.StremioCatalogMerger
import com.orgista.openstream.data.StremioClient
import com.orgista.openstream.data.StremioAddonPresentation
import com.orgista.openstream.data.StremioSearchPolicy
import com.orgista.openstream.data.YoutubeChannelClient
import com.orgista.openstream.data.YoutubeSourcePolicy
import com.orgista.openstream.domain.ContentPolicy
import com.orgista.openstream.domain.ProfileStore
import com.orgista.openstream.domain.RecommendationEngine
import com.orgista.openstream.domain.UpNextPolicy
import com.orgista.openstream.domain.filterCatalog
import com.orgista.openstream.domain.isItemAllowed
import com.orgista.openstream.library.IndexedLibrary
import com.orgista.openstream.library.LibraryIndexer
import com.orgista.openstream.library.LibraryKind
import com.orgista.openstream.library.LibraryStore
import com.orgista.openstream.library.MetadataPrefetchPolicy
import com.orgista.openstream.library.Tmdb
import com.orgista.openstream.player.PlaybackStore
import com.orgista.openstream.offline.OfflineCatalogPolicy
import com.orgista.openstream.offline.OfflineDeviceCapabilities
import com.orgista.openstream.offline.OfflineDeviceProfile
import com.orgista.openstream.offline.OfflineMediaRuntime
import com.orgista.openstream.offline.OfflineQualityPolicy
import com.orgista.openstream.offline.OfflineIdentityResult
import com.orgista.openstream.offline.OfflineStreamIdentityPolicy
import com.orgista.openstream.ui.icons.AppIcons
import com.orgista.openstream.trakt.TraktMediaIdentity
import com.orgista.openstream.streaming.BandwidthRuntime
import com.orgista.openstream.streaming.StreamingPlan
import com.orgista.openstream.streaming.StreamingQualityPolicy
import com.orgista.openstream.iptv.IptvStore
import com.orgista.openstream.iptv.LegacyEmbeddedSourceCacheCleaner
import com.orgista.openstream.iptv.IptvPresentationPolicy
import com.orgista.openstream.iptv.LiveChannelNavigationPolicy
import com.orgista.openstream.arr.ArrPendingDownloadRuntime
import com.orgista.openstream.xr.ImmersiveMediaClassifier
import com.orgista.openstream.xr.VrPlaybackRoutingPolicy
import com.orgista.openstream.xr.WebXrBackend
import com.orgista.openstream.xr.XrRuntimeProbe
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A playback request routed to the player: what to play and how to reach it. */
data class PlaybackRequest(
    val title: String,
    val subtitle: String?,
    val uri: String,
    val nasConnection: NasConnection? = null,
    val mediaId: String? = null,
    val fallbackUris: List<String> = emptyList(),
    val offlineDownloadId: String? = null,
    val traktIdentity: TraktMediaIdentity? = null,
    val streamingPlan: StreamingPlan? = null,
    val httpUserAgent: String? = null,
    val httpReferer: String? = null,
    val isLive: Boolean = false,
    val stremioType: String? = null,
    val stremioId: String? = null,
)

fun playableUris(request: PlaybackRequest): List<String> =
    (listOf(request.uri) + request.fallbackUris)
        .filter { uri ->
            uri.startsWith("https://") || uri.startsWith("http://") ||
                uri.startsWith("smb://")
        }
        .distinct()
        .take(12)

fun livePlaybackRequest(channel: com.orgista.openstream.iptv.IptvChannel): PlaybackRequest = PlaybackRequest(
    title = channel.name,
    subtitle = "Live TV · ${IptvPresentationPolicy.category(channel)}",
    uri = channel.streamUrl,
    mediaId = "iptv:${com.orgista.openstream.iptv.LiveChannelPackagePolicy.channelKey(channel)}",
    fallbackUris = channel.fallbackStreamUrls,
    httpUserAgent = channel.httpUserAgent,
    httpReferer = channel.httpReferer,
    isLive = true,
)

private fun MediaCandidate.playbackPriority(): Int = when (sourceKind) {
    SourceKind.OFFLINE -> 0
    SourceKind.NAS -> 1
    SourceKind.STREMIO -> 2
}

fun bestPlaybackRequest(
    item: MediaItem,
    streams: List<StremioStream> = emptyList(),
    deviceProfile: OfflineDeviceProfile? = null,
    bandwidthMbps: Double? = null,
): PlaybackRequest? {
    val candidate = item.candidates
        .filter { it.sourceKind != SourceKind.OFFLINE || it.offlineIdentityVerified }
        .filter { playableUris(PlaybackRequest(item.title, it.sourceName, it.uri)).isNotEmpty() }
        .minByOrNull(MediaCandidate::playbackPriority)
    if (candidate != null) {
        return PlaybackRequest(
            title = item.title,
            subtitle = candidate.sourceName,
            uri = candidate.uri,
            mediaId = item.id,
            offlineDownloadId = candidate.offlineDownloadId,
            traktIdentity = TraktMediaIdentity.from(item),
            stremioType = if (item.kind == com.orgista.openstream.data.MediaKind.SERIES) "series" else "movie",
            stremioId = item.id.takeIf { it.startsWith("tt") } ?: item.imdbId,
        )
    }

    val identitySafeStreams = streams.filter {
        OfflineStreamIdentityPolicy.classify(item, it) != OfflineIdentityResult.MISMATCH
    }
    val streamingSelection = deviceProfile?.let { profile ->
        StreamingQualityPolicy.select(
            identitySafeStreams,
            profile,
            runtimeMinutes = item.runtimeMinutes.takeIf { it > 0 } ?: 120,
            bandwidthMbps = bandwidthMbps ?: 10_000.0,
        )
    }
    val stream = streamingSelection?.selected?.stream
        ?: deviceProfile?.let { profile -> OfflineQualityPolicy.select(identitySafeStreams, profile, Long.MAX_VALUE)?.stream }
        ?: identitySafeStreams.firstOrNull()
        ?: return null
    val orderedFallbacks = streamingSelection?.fallbacks?.map { it.stream }
        ?: identitySafeStreams.filter { it.url != stream.url }
    return PlaybackRequest(
        title = item.title,
        subtitle = stream.title,
        uri = stream.url,
        mediaId = item.id,
        fallbackUris = orderedFallbacks.asSequence().filter { it.url != stream.url }.map { it.url }.toList(),
        traktIdentity = TraktMediaIdentity.from(item),
        streamingPlan = bandwidthMbps?.let { streamingSelection?.plan },
        stremioType = if (item.kind == com.orgista.openstream.data.MediaKind.SERIES) "series" else "movie",
        stremioId = item.id.takeIf { it.startsWith("tt") } ?: item.imdbId,
    )
}

fun preferredPlaybackRequest(item: MediaItem): PlaybackRequest? = bestPlaybackRequest(item)

sealed interface Screen {
    data object Discover : Screen
    data object Library : Screen
    data object Live : Screen
    data object Search : Screen
    data object Connections : Screen
    data object Settings : Screen
    data object ChannelManager : Screen
    data class Details(val item: MediaItem) : Screen
    data class Player(val request: PlaybackRequest) : Screen
    data class YoutubeVideo(val videoId: String, val title: String) : Screen
}

sealed interface BackDestination {
    data class Navigate(val screen: Screen) : BackDestination
    data object Exit : BackDestination
}

fun backDestination(current: Screen, lastTab: Screen): BackDestination = when (current) {
    is Screen.Details -> BackDestination.Navigate(lastTab)
    is Screen.Player -> BackDestination.Navigate(if (current.request.isLive) Screen.Live else lastTab)
    is Screen.YoutubeVideo -> BackDestination.Navigate(lastTab)
    Screen.ChannelManager -> BackDestination.Navigate(Screen.Connections)
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
    val iptvStore = remember { IptvStore(context.applicationContext) }
    val xrRuntime = remember { XrRuntimeProbe.inspect(context.applicationContext) }
    var offlineStorageRevision by remember { mutableIntStateOf(0) }
    val offlineRuntime = remember(offlineStorageRevision) { OfflineMediaRuntime.get(context.applicationContext) }
    val offlineEntries by offlineRuntime.entries.collectAsState()
    val deviceStatus = rememberHeaderDeviceStatus()
    val appScope = rememberCoroutineScope()
    var policy by remember { mutableStateOf(profileStore.loadActivePolicy()) }
    // JSON catalog caches can be several megabytes. Decoding them in remember
    // blocked the first interactive frame, so a tap on Live during launch was
    // silently delayed behind unrelated Discover data. Paint navigation first
    // and adopt the cached snapshots from a worker thread when ready.
    var indexed by remember { mutableStateOf<IndexedLibrary?>(null) }
    var indexing by remember { mutableStateOf(false) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var playbackRevision by remember { mutableIntStateOf(0) }
    var addonRevision by remember { mutableIntStateOf(0) }
    var iptvRevision by remember { mutableIntStateOf(0) }
    var addonCatalog by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var addonCatalogs by remember { mutableStateOf<List<com.orgista.openstream.data.AddonCatalogSection>>(emptyList()) }
    var addonRecommendations by remember { mutableStateOf<List<AddonCatalogSection>>(emptyList()) }
    var youtubeCatalogs by remember { mutableStateOf<List<AddonCatalogSection>>(emptyList()) }
    val hasLibraries = remember(libraryRevision) { libraryStore.folders().isNotEmpty() }

    LaunchedEffect(Unit) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val (libraryCache, rawAddonCache) = withContext(Dispatchers.IO) {
            coroutineScope {
                val removedLegacyCaches = async {
                    LegacyEmbeddedSourceCacheCleaner.clean(context.applicationContext)
                }
                val library = async {
                    com.orgista.openstream.library.LibraryCache.load(context.applicationContext)
                }
                val addons = async { StremioCatalogCache.load(context.applicationContext) }
                val removed = removedLegacyCaches.await()
                if (removed > 0) Logs.event("removed $removed obsolete compiled-source cache files")
                library.await() to addons.await()
            }
        }
        val addonCache = ConfiguredStremioCatalogPolicy.retain(
            rawAddonCache,
            connectionStore.stremioAddons(),
        )
        if (rawAddonCache != null && addonCache == null) {
            withContext(Dispatchers.IO) { StremioCatalogCache.remove(context.applicationContext) }
        }
        if (indexed == null) indexed = libraryCache
        if (addonCatalog.isEmpty()) addonCatalog = addonCache?.items.orEmpty()
        if (addonCatalogs.isEmpty()) addonCatalogs = addonCache?.sections.orEmpty()
        Logs.event(
            "startup caches decoded off main in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms: " +
                "${libraryCache?.all?.size ?: 0} library, ${addonCache?.items?.size ?: 0} add-on titles",
        )
    }

    // Prepare user-saved Channel Manager entries while Discover is already on
    // screen; configured providers refresh after Live opens.
    LaunchedEffect(iptvRevision, deviceStatus.online) {
        if (!deviceStatus.online) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            iptvStore.warmCachedSession(iptvRevision)
        }
    }

    LaunchedEffect(deviceStatus.online) {
        if (!deviceStatus.online) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            ArrPendingDownloadRuntime.flush(context.applicationContext)
        }
        if (result.sent > 0) {
            val destinations = result.destinations.joinToString(" and ") { it.displayName }
            Toast.makeText(
                context,
                "Sent ${result.sent} saved download${if (result.sent == 1) "" else "s"} to $destinations",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

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
        val result = withContext(Dispatchers.Default) {
            IndexedLibrary(
                movies.sortedBy { it.title.lowercase() },
                shows.sortedBy { it.title.lowercase() },
            )
        }
        indexed = result
        withContext(Dispatchers.IO) {
            com.orgista.openstream.library.LibraryCache.save(context.applicationContext, result)
        }
        indexing = false
    }

    // User-configured discovery catalogs refresh every six hours and fall back
    // to their last app-private cache when a provider is offline. A Sources
    // change restarts this loop immediately.
    LaunchedEffect(addonRevision, deviceStatus.online) {
        if (!deviceStatus.online) return@LaunchedEffect
        var firstPass = true
        while (true) {
            val configuredAddons = connectionStore.stremioAddons()
            val cached = withContext(Dispatchers.IO) {
                ConfiguredStremioCatalogPolicy.retain(
                    StremioCatalogCache.load(context.applicationContext),
                    configuredAddons,
                )
            }
            val shouldRefresh = addonRevision > 0 || !firstPass || cached?.isFresh() != true || cached?.sections.isNullOrEmpty()
            if (shouldRefresh) {
                val activeCatalogAddons = configuredAddons.filter {
                    it.enabled && "catalog" in it.resources
                }
                val fetchedSections = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Fetch every add-on's catalog concurrently; order and the
                    // per-add-on failure isolation match the sequential version.
                    coroutineScope {
                        activeCatalogAddons.map { addon ->
                            async {
                                runCatching { StremioClient.catalogSectionsFor(addon) }
                                    .onFailure { Logs.warn("catalog refresh failed for ${addon.name}") }
                                    .getOrDefault(emptyList())
                            }
                        }.flatMap { it.await() }
                    }
                }
                if (fetchedSections.isNotEmpty()) {
                    val cachedItems = cached?.items.orEmpty()
                    val enrichedSections = fetchedSections.map { section ->
                        section.copy(
                            items = StremioCatalogMerger.retainCachedMetadata(section.items, cachedItems),
                        )
                    }
                    val merged = StremioCatalogMerger.merge(emptyList(), enrichedSections.flatMap { it.items })
                    addonCatalog = merged
                    addonCatalogs = enrichedSections
                    withContext(Dispatchers.IO) {
                        StremioCatalogCache.saveSections(context.applicationContext, enrichedSections, merged)
                    }
                    Logs.event("add-on catalog refreshed: ${enrichedSections.size} categories, ${merged.size} titles")
                } else if (activeCatalogAddons.isEmpty()) {
                    addonCatalog = emptyList()
                    addonCatalogs = emptyList()
                    withContext(Dispatchers.IO) { StremioCatalogCache.remove(context.applicationContext) }
                } else if (cached != null) {
                    addonCatalog = cached.items
                    addonCatalogs = cached.sections
                }
            }
            firstPass = false
            kotlinx.coroutines.delay(6L * 60L * 60L * 1_000L)
        }
    }

    // Each approved YouTube channel becomes one recent-uploads rail. Keep the
    // previous good rail for a channel when a transient refresh fails, but
    // immediately remove disabled/deleted channels from the visible catalog.
    LaunchedEffect(addonRevision, deviceStatus.online) {
        if (!deviceStatus.online) {
            youtubeCatalogs = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            val channels = connectionStore.youtubeChannels().filter { it.enabled }
            if (channels.isEmpty()) {
                youtubeCatalogs = emptyList()
                return@LaunchedEffect
            }
            val previous = youtubeCatalogs.associateBy { it.addonId }
            val sections = withContext(Dispatchers.IO) {
                // Refresh every channel concurrently. Each channel keeps its own
                // fallback to the previous good rail, so one failure never drops
                // the others, and result order is preserved.
                coroutineScope {
                    channels.map { channel ->
                        async {
                            runCatching {
                                val feed = YoutubeChannelClient.recentVideos(channel)
                                YoutubeSourcePolicy.section(
                                    channel.copy(name = feed.channelTitle.ifBlank { channel.name }),
                                    feed.videos,
                                )
                            }.onFailure {
                                Logs.warn("YouTube channel refresh failed for ${channel.name}")
                            }.getOrNull() ?: previous[channel.id]
                        }
                    }.map { it.await() }
                }.filterNotNull().filter { it.items.isNotEmpty() }
            }
            youtubeCatalogs = sections
            Logs.event("YouTube sources refreshed: ${sections.size} channels, ${sections.sumOf { it.items.size }} videos")
            kotlinx.coroutines.delay(60L * 60L * 1_000L)
        }
    }

    // Warm the small set of sparse titles most likely to be opened next. The
    // result is merged into every provider rail and persisted, so Back and a
    // later app launch render details immediately while refresh happens later.
    LaunchedEffect(libraryRevision, deviceStatus.online, addonCatalog.map { it.id }) {
        if (!deviceStatus.online || addonCatalog.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        val loader = com.orgista.openstream.library.MetadataLoader(
            libraryStore.reusableTmdbCredential(),
            libraryStore.reusableOmdbCredential(),
        )
        val metaAddons = connectionStore.stremioAddons().filter { it.enabled && "meta" in it.resources }
        if (!loader.active && metaAddons.isEmpty()) return@LaunchedEffect
        val targets = MetadataPrefetchPolicy.select(addonCatalog, limit = 24)
        if (targets.isEmpty()) return@LaunchedEffect
        val enrichedItems = withContext(Dispatchers.IO) {
            targets.chunked(4).flatMap { batch ->
                coroutineScope {
                    batch.map { target ->
                        async {
                            var result = target
                            if (loader.active) {
                                runCatching { loader.enrich(result) }.getOrNull()?.let { result = it }
                            }
                            val metaId = result.imdbId ?: result.id.takeIf { it.startsWith("tt") }
                            if (metaId != null) {
                                val type = if (result.kind == com.orgista.openstream.data.MediaKind.SERIES) {
                                    "series"
                                } else {
                                    "movie"
                                }
                                metaAddons.asSequence()
                                    .mapNotNull { addon ->
                                        runCatching { StremioClient.metaFor(addon, type, metaId) }.getOrNull()
                                    }
                                    .firstOrNull()
                                    ?.let { result = StremioCatalogMerger.mergeItem(result, it) }
                            }
                            result
                        }
                    }.map { it.await() }
                }
            }
        }
        val byIdentity = enrichedItems.associateBy { it.imdbId ?: it.id }
        fun mergePrefetched(existing: MediaItem): MediaItem =
            byIdentity[existing.imdbId ?: existing.id]?.let { StremioCatalogMerger.mergeItem(existing, it) } ?: existing
        addonCatalog = addonCatalog.map(::mergePrefetched)
        addonCatalogs = addonCatalogs.map { section -> section.copy(items = section.items.map(::mergePrefetched)) }
        withContext(Dispatchers.IO) {
            StremioCatalogCache.saveSections(context.applicationContext, addonCatalogs, addonCatalog)
        }
        Logs.event("metadata cache warmed for ${enrichedItems.size} titles")
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
    val recommendationCandidates = remember(indexed, hasLibraries, addonCatalog) {
        val local = when {
            indexed != null && indexed?.isEmpty == false -> indexed?.all.orEmpty()
            hasLibraries -> emptyList()
            else -> emptyList()
        }
        StremioCatalogMerger.merge(local, addonCatalog)
            .filter { it.kind != com.orgista.openstream.data.MediaKind.VIDEO && !it.imdbId.isNullOrBlank() }
    }
    val recommendationSeed = remember(recommendationCandidates, playbackRevision) {
        val signals = playbackStore.viewingSignals(recommendationCandidates.map { it.id })
        recommendationCandidates.maxByOrNull { signals[it.id]?.lastPlayedAtMs ?: 0L }
    }

    // Watch Next exposes Stremio detail links through its stream resource rather
    // than a catalog. Adapt those links into a normal Discover rail and resolve
    // their artwork/details through the user's enabled metadata add-ons.
    LaunchedEffect(addonRevision, playbackRevision, deviceStatus.online, recommendationSeed?.imdbId) {
        if (!deviceStatus.online || recommendationSeed == null) {
            addonRecommendations = emptyList()
            return@LaunchedEffect
        }
        val addons = connectionStore.stremioAddons().filter { it.enabled }
        val providers = addons.filter {
            "stream" in it.resources && StremioAddonPresentation.isRecommendationProvider(it)
        }
        if (providers.isEmpty()) {
            addonRecommendations = emptyList()
            return@LaunchedEffect
        }
        val metaAddons = addons.filter { "meta" in it.resources }
        val known = recommendationCandidates.associateBy { it.imdbId ?: it.id }
        val seedType = if (recommendationSeed.kind == com.orgista.openstream.data.MediaKind.SERIES) "series" else "movie"
        val seedId = recommendationSeed.imdbId ?: return@LaunchedEffect
        val resolvedSections = withContext(Dispatchers.IO) {
            providers.mapNotNull { provider ->
                val references = runCatching {
                    StremioClient.recommendationsFor(provider, seedType, seedId)
                }.onFailure {
                    Logs.warn("recommendations failed for ${provider.name}")
                }.getOrDefault(emptyList())
                if (references.isEmpty()) return@mapNotNull null
                val items = references.take(20).chunked(4).flatMap { batch ->
                    coroutineScope {
                        batch.map { reference ->
                            async {
                                known[reference.id]
                                    ?: metaAddons.asSequence().mapNotNull { addon ->
                                        runCatching {
                                            StremioClient.metaFor(addon, reference.type, reference.id)
                                        }.getOrNull()
                                    }.firstOrNull()
                                    ?: MediaItem(
                                        id = reference.id,
                                        kind = if (reference.type == "series") {
                                            com.orgista.openstream.data.MediaKind.SERIES
                                        } else {
                                            com.orgista.openstream.data.MediaKind.MOVIE
                                        },
                                        title = reference.title,
                                        synopsis = reference.description.orEmpty(),
                                        year = 0,
                                        rating = "",
                                        runtimeMinutes = 0,
                                        imdbId = reference.id.takeIf { it.startsWith("tt") },
                                    )
                            }
                        }.map { it.await() }
                    }
                }.distinctBy { it.imdbId ?: it.id }.filterNot { (it.imdbId ?: it.id) == seedId }
                AddonCatalogSection(
                    addonId = provider.manifestId,
                    addonName = provider.name,
                    catalogId = "recommendations:$seedId",
                    catalogName = "If you liked ${recommendationSeed.title}",
                    items = items,
                ).takeIf { it.items.isNotEmpty() }
            }
        }
        addonRecommendations = resolvedSections
        Logs.event(
            "recommendations refreshed: ${resolvedSections.sumOf { it.items.size }} titles from " +
                "${resolvedSections.size} add-on(s)",
        )
    }

    // Only real libraries and configured add-ons belong in production.
    val library = indexed
    val onlineCatalog = remember(
        activePolicy,
        library,
        hasLibraries,
        addonCatalog,
        addonRecommendations,
        youtubeCatalogs,
    ) {
        val local = when {
            library != null && !library.isEmpty -> library.all
            hasLibraries -> emptyList() // configured but first scan not done yet
            else -> emptyList()
        }
        StremioCatalogMerger.merge(
            local,
            addonCatalog + addonRecommendations.flatMap { it.items } + youtubeCatalogs.flatMap { it.items },
        )
    }
    val discoverAddonCatalogs = remember(addonCatalogs, addonRecommendations, youtubeCatalogs) {
        addonCatalogs + addonRecommendations + youtubeCatalogs
    }
    val offlineItems = remember(offlineEntries) {
        offlineEntries.filter { it.completed && it.identityVerified }.map { it.item }
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
    val discoverFeedPreferences = remember(addonRevision) { connectionStore.discoverFeedPreferences() }
    // The ranked catalog and Up Next both need viewing signals for the same id
    // set, so compute it once per revision and share it rather than making a
    // separate whole-catalog pass here.
    val catalogHistory = remember(catalog, playbackRevision) {
        playbackStore.viewingSignals(catalog.map { it.id })
    }
    val upNextItems = remember(catalog, youtubeCatalogs, playbackRevision, discoverFeedPreferences) {
        UpNextPolicy.select(
            rankedCatalog = catalog,
            youtubeSections = youtubeCatalogs,
            history = catalogHistory,
        )
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Discover) }
    var lastTab by remember { mutableStateOf<Screen>(Screen.Discover) }

    val navigate: (Screen) -> Unit = { next ->
        Logs.event("navigate: ${next::class.simpleName}")
        if (next is Screen.Discover || next is Screen.Library || next is Screen.Live || next is Screen.Search ||
            next is Screen.Connections || next is Screen.Settings
        ) {
            lastTab = next
        }
        screen = next
    }

    val playGuarded: (MediaItem, PlaybackRequest) -> Unit = playGuarded@{ item, request ->
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
            if (resolved.traktIdentity == null) resolved = resolved.copy(traktIdentity = TraktMediaIdentity.from(item))
            val immersive = ImmersiveMediaClassifier.classify(
                Uri.parse(resolved.uri),
                "${item.title} ${resolved.subtitle.orEmpty()}",
                item.synopsis,
                resolved.subtitle.orEmpty(),
                "",
            )
            val activity = context as? Activity
            val webXrBackend = WebXrBackend(xrRuntime)
            if (activity != null && webXrBackend.isAvailable(activity) &&
                VrPlaybackRoutingPolicy.shouldUseImmersivePlayer(
                    xrRuntime.immersiveRuntimeAvailable(),
                    immersive,
                )
            ) {
                val launched = webXrBackend.launch(activity, immersive)
                if (launched.launched()) {
                    Logs.event("play immersive: ${item.title} · ${immersive.projection().name}")
                    return@playGuarded
                }
                Toast.makeText(context, launched.message(), Toast.LENGTH_LONG).show()
            }
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

    val openItem: (MediaItem) -> Unit = { item ->
        val youtubeVideoId = YoutubeSourcePolicy.videoId(item)
        if (youtubeVideoId != null) {
            navigate(Screen.YoutubeVideo(youtubeVideoId, item.title))
        } else {
            navigate(Screen.Details(item))
        }
    }

    // Hoisted so Details/Player don't re-parse the add-on JSON — or rebuild the
    // metadata loader — on every recomposition. These screens recompose on the
    // 30s clock tick and battery broadcasts while open; keying on the addon /
    // library revisions (and the online flag) keeps the work to actual changes.
    val detailAddons = remember(addonRevision, deviceStatus.online) {
        if (deviceStatus.online) connectionStore.stremioAddons().filter { it.enabled } else emptyList()
    }
    val subtitleAddons = remember(addonRevision, deviceStatus.online) {
        if (deviceStatus.online) {
            connectionStore.stremioAddons().filter { it.enabled && "subtitles" in it.resources }
        } else {
            emptyList()
        }
    }
    val metadataLoader = remember(libraryRevision) {
        com.orgista.openstream.library.MetadataLoader(
            libraryStore.reusableTmdbCredential(),
            libraryStore.reusableOmdbCredential(),
        )
    }

    when (val current = screen) {
        is Screen.Details -> {
            DetailsScreen(
                item = current.item,
                kidsMode = kidsMode,
                addons = detailAddons,
                offlineRuntime = offlineRuntime,
                playbackStore = playbackStore,
                online = deviceStatus.online,
                metadataLoader = metadataLoader,
                onMetadataEnriched = { enriched ->
                    indexed?.let { currentLibrary ->
                        val updated = currentLibrary.copy(
                            movies = currentLibrary.movies.map { if (it.id == enriched.id) enriched else it },
                            shows = currentLibrary.shows.map { if (it.id == enriched.id) enriched else it },
                        )
                        indexed = updated
                        com.orgista.openstream.library.LibraryCache.save(context.applicationContext, updated)
                    }
                    fun MediaItem.matchesMetadata(other: MediaItem): Boolean =
                        id == other.id || (imdbId != null && imdbId == other.imdbId)
                    val updatedAddonCatalog = addonCatalog.map { existing ->
                        if (existing.matchesMetadata(enriched)) {
                            StremioCatalogMerger.mergeItem(existing, enriched)
                        } else {
                            existing
                        }
                    }
                    val updatedAddonSections = addonCatalogs.map { section ->
                        section.copy(
                            items = section.items.map { existing ->
                                if (existing.matchesMetadata(enriched)) {
                                    StremioCatalogMerger.mergeItem(existing, enriched)
                                } else {
                                    existing
                                }
                            },
                        )
                    }
                    if (updatedAddonCatalog != addonCatalog || updatedAddonSections != addonCatalogs) {
                        addonCatalog = updatedAddonCatalog
                        addonCatalogs = updatedAddonSections
                        appScope.launch(Dispatchers.IO) {
                            StremioCatalogCache.saveSections(
                                context.applicationContext,
                                updatedAddonSections,
                                updatedAddonCatalog,
                            )
                        }
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
                subtitleAddons = subtitleAddons,
                deleteOfflineAfterWatched = libraryStore.deleteOfflineAfterWatched,
                playbackStore = playbackStore,
                onBack = { screen = if (current.request.isLive) Screen.Live else lastTab },
                onExit = onExit,
                onProgressSaved = { playbackRevision++ },
                onChangeLiveChannel = { direction ->
                    val snapshot = iptvStore.sessionSnapshot()
                    val lineup = snapshot.preparedGuide?.activeChannels.orEmpty()
                    val currentKey = current.request.mediaId?.removePrefix("iptv:").orEmpty()
                    val adjacent = LiveChannelNavigationPolicy.adjacent(
                        channels = lineup,
                        currentChannelKey = currentKey,
                        direction = direction,
                        currentChannelName = current.request.title,
                    )
                    if (adjacent != null) {
                        Logs.event("live channel ${if (direction > 0) "up" else "down"}: ${adjacent.name}")
                        screen = Screen.Player(livePlaybackRequest(adjacent))
                    }
                },
            )
            return
        }
        is Screen.YoutubeVideo -> {
            YoutubeVideoPlayerScreen(
                videoId = current.videoId,
                title = current.title,
                onBack = { screen = lastTab },
            )
            return
        }
        Screen.ChannelManager -> {
            LiveChannelManagerScreen(
                store = iptvStore,
                allChannels = iptvStore.sessionChannels(),
                onChanged = { iptvRevision++ },
                onPackagesChanged = { iptvRevision++ },
                onOpenSources = { navigate(Screen.Connections) },
                onBack = { navigate(Screen.Connections) },
            )
            return
        }
        else -> Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(OpenStreamColors.Canvas)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ),
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
        )
        Box(Modifier.fillMaxSize().clipToBounds()) {
            when (screen) {
                Screen.Discover -> DiscoverScreen(
                    catalog = catalog,
                    addonCatalogs = if (deviceStatus.online) discoverAddonCatalogs else emptyList(),
                    feedPreferences = discoverFeedPreferences,
                    upNextItems = upNextItems,
                    offlineMode = !deviceStatus.online,
                    onOpen = openItem,
                    onPlay = { item ->
                        val youtubeVideoId = YoutubeSourcePolicy.videoId(item)
                        if (youtubeVideoId != null) {
                            navigate(Screen.YoutubeVideo(youtubeVideoId, item.title))
                            return@DiscoverScreen
                        }
                        if (item.kind == com.orgista.openstream.data.MediaKind.SERIES) {
                            navigate(Screen.Details(item))
                            return@DiscoverScreen
                        }
                        val request = preferredPlaybackRequest(item)
                        if (request != null) {
                            playGuarded(item, request)
                        } else {
                            val streamAddons = connectionStore.stremioAddons().filter {
                                it.enabled && "stream" in it.resources
                            }
                            val imdbId = item.imdbId
                            if (!kidsMode && imdbId != null && streamAddons.isNotEmpty()) {
                                Toast.makeText(context, "Optimizing for your network…", Toast.LENGTH_SHORT).show()
                                appScope.launch {
                                    val type = if (item.kind == com.orgista.openstream.data.MediaKind.SERIES) "series" else "movie"
                                    val streams = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        streamAddons.asSequence().flatMap { addon ->
                                            runCatching { StremioClient.streamsFor(addon, type, imdbId) }
                                                .getOrDefault(emptyList())
                                                .asSequence()
                                        }.distinctBy { it.url }.toList()
                                    }
                                    val deviceProfile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        OfflineDeviceCapabilities.inspect(context.applicationContext)
                                    }
                                    val bandwidth = runCatching {
                                        BandwidthRuntime.forPlayback(context.applicationContext)
                                    }.getOrNull()
                                    val request = bestPlaybackRequest(item, streams, deviceProfile, bandwidth?.mbps)
                                    if (request == null) {
                                        Toast.makeText(context, "No directly playable stream found", Toast.LENGTH_SHORT).show()
                                        navigate(Screen.Details(item))
                                    } else {
                                        playGuarded(item, request)
                                    }
                                }
                            } else {
                                navigate(Screen.Details(item))
                            }
                        }
                    },
                )
                Screen.Library -> LibraryScreen(catalog, openItem)
                Screen.Live -> LiveTvScreen(
                    store = iptvStore,
                    revision = iptvRevision,
                    online = deviceStatus.online,
                    onOpenSources = { navigate(Screen.Connections) },
                    onPlay = { channel ->
                        if (kidsMode) {
                            Toast.makeText(context, "Live TV is not available in Kids Mode", Toast.LENGTH_SHORT).show()
                        } else {
                            navigate(Screen.Player(livePlaybackRequest(channel)))
                        }
                    },
                )
                Screen.Search -> SearchScreen(
                    catalog = catalog,
                    online = deviceStatus.online,
                    remoteSearch = { query ->
                        withContext(Dispatchers.IO) {
                            val searchAddons = connectionStore.stremioAddons()
                                .filter { addon -> StremioSearchPolicy.catalogsFor(addon).isNotEmpty() }
                                .take(6)
                            val searches = searchAddons.flatMap { addon ->
                                StremioSearchPolicy.catalogsFor(addon).map { catalog -> addon to catalog }
                            }.take(12)
                            coroutineScope {
                                searches.map { (addon, searchCatalog) ->
                                    async { StremioClient.searchCatalog(addon, searchCatalog, query) }
                                }.flatMap { it.await() }
                                    .distinctBy { item -> item.imdbId ?: item.id }
                                    .let { results -> filterCatalog(results, activePolicy) }
                            }
                        }
                    },
                    onOpen = openItem,
                )
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
                    iptvStore = iptvStore,
                    onIptvChanged = { iptvRevision++ },
                    onOpenChannelManager = { navigate(Screen.ChannelManager) },
                    onAddonsChanged = { addonRevision++ },
                )
                Screen.Settings -> SettingsScreen(
                    policy = activePolicy,
                    libraryStore = libraryStore,
                    offlineRuntime = offlineRuntime,
                    onTmdbKeyChanged = { libraryRevision++ },
                    onOfflineStorageChanged = { requested ->
                        val changed = OfflineMediaRuntime.configureStorage(context.applicationContext, requested)
                        if (changed) {
                            offlineStorageRevision++
                            Toast.makeText(context, "Offline storage updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Remove current offline downloads before changing storage",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
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
) {
    var menuOpen by remember { mutableStateOf(false) }
    val primaryTabs = primaryTopDestinations().map { destination ->
        when (destination) {
            TopDestination.DISCOVER -> Tab(Screen.Discover, "Discover")
            TopDestination.LIBRARY -> Tab(Screen.Library, "Library")
            TopDestination.LIVE -> Tab(Screen.Live, "Live")
            else -> error("$destination is not a primary tab")
        }
    }
    val headerStatus = libraryHeaderStatus(indexing, hasLibraries, titleCount, metadataEnabled)
    val configuration = LocalConfiguration.current
    val compact = TopNavLayoutPolicy.isCompact(configuration.screenWidthDp, configuration.screenHeightDp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OpenStreamColors.Canvas),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compact) 16.dp else 26.dp,
                    end = if (compact) 8.dp else 26.dp,
                    top = if (compact) 7.dp else 10.dp,
                    bottom = if (compact) 2.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clickable { onNavigate(Screen.Discover) }
                    .padding(
                        end = 8.dp,
                        top = 7.dp,
                        bottom = (TopNavLayoutPolicy.brandBottomPaddingDp(compact) + 7).dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.openstream_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 24.dp else 27.dp),
                )
                Text(
                    "OpenStream",
                    fontSize = if (compact) 17.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.14).sp,
                    color = OpenStreamColors.Text,
                )
            }
            if (kidsMode) {
                Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
                KidsBadge()
            }
            if (!deviceStatus.online) {
                Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
                OfflineBadge()
            }
            if (!compact) {
                Spacer(Modifier.width(38.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    primaryTabs.forEach { tab ->
                        TabButton(tab.label, current == tab.screen) { onNavigate(tab.screen) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (indexing) {
                OpenStreamLoadingIndicator(role = LoadingIndicatorRole.INLINE)
                Spacer(Modifier.width(if (compact) 7.dp else 14.dp))
            }
            if (!compact) {
                Text(deviceStatus.time, color = OpenStreamColors.TextSoft, fontSize = 13.sp)
                Spacer(Modifier.width(12.dp))
            }
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
                }
            }
        }
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                primaryTabs.forEach { tab ->
                    TabButton(
                        label = tab.label,
                        selected = current == tab.screen,
                        modifier = Modifier.weight(1f),
                        compact = true,
                    ) { onNavigate(tab.screen) }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 17.dp,
                vertical = if (compact) 5.dp else 7.dp,
            ),
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
