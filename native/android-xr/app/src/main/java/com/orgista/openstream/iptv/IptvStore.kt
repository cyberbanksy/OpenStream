package com.orgista.openstream.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class IptvStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openstream.iptv.v1", Context.MODE_PRIVATE)
    private val catalogCache = IptvCatalogCache(context.applicationContext)
    private val guideCache = IptvGuideCache(context.applicationContext)

    fun sources(): List<IptvSource> = BUILT_INS + listOfNotNull(custom())

    fun sessionSnapshot(): IptvSessionSnapshot = IptvSessionCatalog.snapshot

    fun sessionChannels(): List<IptvChannel> = sessionSnapshot().channels

    fun sessionGuidePrograms(): List<IptvGuideProgram> = sessionSnapshot().programs

    fun updateSessionChannels(channels: List<IptvChannel>) {
        synchronized(IptvSessionCatalog) {
            // Stale-while-refresh: the visible guide and player channel
            // controls keep the last usable lineup until LiveTvScreen installs
            // the replacement prepared off the main thread.
            IptvSessionCatalog.snapshot = IptvSessionCatalog.snapshot.copy(channels = channels)
        }
    }

    fun updateSessionGuidePrograms(programs: List<IptvGuideProgram>) {
        synchronized(IptvSessionCatalog) {
            // Keep the prior prepared lineup usable while its search/guide
            // replacement is rebuilt off main. Channel navigation depends on
            // that stable lineup and must not disappear during an EPG refresh.
            IptvSessionCatalog.snapshot = IptvSessionCatalog.snapshot.copy(programs = programs)
        }
    }

    fun updateSessionPreparedGuide(preparedGuide: LivePreparedGuide) {
        synchronized(IptvSessionCatalog) {
            IptvSessionCatalog.snapshot = IptvSessionCatalog.snapshot.copy(preparedGuide = preparedGuide)
        }
    }

    fun markSessionRefreshed(revision: Int, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(IptvSessionCatalog) {
            IptvSessionCatalog.snapshot = IptvSessionCatalog.snapshot.copy(
                revision = revision,
                refreshedAtMillis = nowMillis,
            )
        }
    }

    fun custom(): IptvSource? {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            IptvSource(
                id = CUSTOM_ID,
                name = json.optString("name", "My IPTV").ifBlank { "My IPTV" },
                type = IptvSourceType.valueOf(json.getString("type")),
                endpoint = json.getString("endpoint"),
                username = json.optString("username"),
                password = json.optString("password"),
            )
        }.getOrNull()
    }

    fun save(source: IptvSource) {
        require(!source.builtIn)
        require(IptvEndpointPolicy.isAllowed(source.endpoint)) { "Enter an HTTP or HTTPS IPTV server address." }
        if (source.type == IptvSourceType.XTREAM) {
            require(source.username.isNotBlank() && source.password.isNotBlank()) { "Username and password are required." }
        }
        val json = JSONObject()
            .put("name", source.name)
            .put("type", source.type.name)
            .put("endpoint", IptvEndpointPolicy.normalize(source.endpoint))
            .put("username", source.username)
            .put("password", source.password)
        prefs.edit().putString(KEY_CUSTOM, json.toString()).apply()
        guideCache.remove(CUSTOM_ID)
    }

    fun removeCustom() {
        prefs.edit().remove(KEY_CUSTOM).apply()
        guideCache.remove(CUSTOM_ID)
    }

    fun cachedBuiltInChannels(): List<IptvChannel> = BUILT_INS.flatMap { catalogCache.load(it.id) }

    /**
     * Primes the process-local Live session from the small public catalog cache.
     * Private provider credentials/routes are intentionally never persisted here.
     */
    fun warmCachedSession(revision: Int): Boolean {
        val snapshot = sessionSnapshot()
        if (snapshot.channels.isNotEmpty() && snapshot.revision == revision && snapshot.preparedGuide != null) {
            return false
        }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val manual = manualChannels()

        // Free-TV is a deliberately tiny bundled/public cache. Install it as
        // the first snapshot before touching IPTV-org's much larger catalog,
        // so a cold-process Live tap has useful rows to paint immediately.
        // The complete searchable catalog replaces it below in the same
        // worker call. Private configured-provider routes are never persisted.
        if (snapshot.channels.isEmpty() || snapshot.revision != revision || snapshot.preparedGuide == null) {
            val quickChannels = IptvCatalogPolicy.mergeForPlayback(catalogCache.load(FREE_TV.id) + manual)
            if (quickChannels.isNotEmpty()) {
                val quickPrepared = LivePreparedGuidePolicy.build(
                    channels = quickChannels,
                    programs = snapshot.programs,
                    enabledPackageIds = enabledPackageIds(),
                    favoriteChannelKeys = favoriteChannelKeys(),
                )
                synchronized(IptvSessionCatalog) {
                    IptvSessionCatalog.snapshot = IptvSessionSnapshot(
                        channels = quickChannels,
                        programs = snapshot.programs,
                        revision = revision,
                        refreshedAtMillis = 0L,
                        preparedGuide = quickPrepared,
                    )
                }
                com.orgista.openstream.Logs.event(
                    "live quick cache ready in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms: " +
                        "${quickPrepared.activeChannels.size} channels",
                )
            }
        }

        val current = sessionSnapshot()
        val cached = if (current.channels.isNotEmpty() && current.revision == revision &&
            current.channels.any { it.sourceId == IPTV_ORG_US.id }
        ) {
            current.channels
        } else {
            IptvCatalogPolicy.mergeForPlayback(cachedBuiltInChannels() + manual)
        }
        if (cached.isEmpty()) return false
        val programs = current.programs
        val prepared = LivePreparedGuidePolicy.build(
            channels = cached,
            programs = programs,
            enabledPackageIds = enabledPackageIds(),
            favoriteChannelKeys = favoriteChannelKeys(),
        )
        synchronized(IptvSessionCatalog) {
            IptvSessionCatalog.snapshot = IptvSessionSnapshot(
                channels = cached,
                programs = programs,
                revision = revision,
                // Zero means this is an immediate paint snapshot, not a fresh
                // provider result. Live still refreshes provider routes after
                // the first frame and marks the completed session below.
                refreshedAtMillis = 0L,
                preparedGuide = prepared,
            )
        }
        com.orgista.openstream.Logs.event(
            "live full cache ready in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms: " +
                "${prepared.allChannels.size} catalog, ${prepared.activeChannels.size} visible",
        )
        return true
    }

    fun cacheBuiltInChannels(source: IptvSource, channels: List<IptvChannel>) {
        if (source.builtIn && channels.all { it.sourceId == source.id }) catalogCache.save(source.id, channels)
    }

    fun cachedGuidePrograms(sourceId: String, nowMillis: Long): List<IptvGuideProgram> =
        guideCache.load(sourceId, nowMillis)

    fun cacheGuidePrograms(sourceId: String, programs: List<IptvGuideProgram>) {
        guideCache.save(sourceId, programs)
    }

    fun markGuideRefreshAttempt(sourceId: String) {
        guideCache.markRefreshAttempt(sourceId)
    }

    fun isGuideCacheFresh(sourceId: String, nowMillis: Long, hasPrograms: Boolean): Boolean =
        guideCache.isFresh(sourceId, nowMillis, hasPrograms)

    fun enabledPackageIds(): Set<String> {
        val stored = prefs.getStringSet(KEY_ENABLED_PACKAGES, null)?.toSet()
            ?: return LiveChannelPackagePolicy.defaultEnabledPackageIds
        val currentIds = LiveChannelPackagePolicy.orderedPackages.mapTo(hashSetOf()) { it.id }
        val migrated = stored.filterTo(linkedSetOf()) { it in currentIds }
        return if (migrated.isEmpty() && stored.isNotEmpty()) {
            LiveChannelPackagePolicy.defaultEnabledPackageIds
        } else {
            migrated
        }
    }

    fun setPackageEnabled(packageId: String, enabled: Boolean) {
        val updated = enabledPackageIds().toMutableSet().apply {
            if (enabled) add(packageId) else remove(packageId)
        }
        prefs.edit().putStringSet(KEY_ENABLED_PACKAGES, updated).apply()
    }

    fun favoriteChannelKeys(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()

    fun setFavorite(channel: IptvChannel, favorite: Boolean) {
        val updated = favoriteChannelKeys().toMutableSet().apply {
            val routeKeys = buildSet {
                add(LiveChannelPackagePolicy.channelKey(channel))
                IptvSessionCatalog.snapshot.channels.forEach { candidate ->
                    if (candidate.streamUrl == channel.streamUrl) add(LiveChannelPackagePolicy.channelKey(candidate))
                }
            }
            if (favorite) addAll(routeKeys) else removeAll(routeKeys)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    fun manualChannels(): List<IptvChannel> {
        val raw = prefs.getString(KEY_MANUAL_CHANNELS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val name = json.optString("name").trim()
                    val url = json.optString("streamUrl").trim()
                    if (!ManualLiveChannelPolicy.isValid(name, url)) continue
                    add(
                        IptvChannel(
                            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                            sourceId = ManualLiveChannelPolicy.SOURCE_ID,
                            sourceName = "My Channels",
                            name = name,
                            group = json.optString("packageId", LiveChannelPackage.LOCAL.id),
                            logoUrl = json.optString("logoUrl"),
                            streamUrl = url,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addManualChannel(
        name: String,
        streamUrl: String,
        packageInfo: LiveChannelPackage,
        logoUrl: String = "",
    ): IptvChannel {
        require(ManualLiveChannelPolicy.isValid(name, streamUrl)) { "Enter a channel name and an HTTP or HTTPS stream address." }
        val channel = IptvChannel(
            id = UUID.randomUUID().toString(),
            sourceId = ManualLiveChannelPolicy.SOURCE_ID,
            sourceName = "My Channels",
            name = name.trim(),
            group = packageInfo.id,
            logoUrl = logoUrl.trim(),
            streamUrl = streamUrl.trim(),
        )
        writeManualChannels((manualChannels() + channel).takeLast(MAX_MANUAL_CHANNELS))
        setFavorite(channel, true)
        return channel
    }

    fun removeManualChannel(channelId: String) {
        val channel = manualChannels().firstOrNull { it.id == channelId }
        writeManualChannels(manualChannels().filterNot { it.id == channelId })
        if (channel != null) setFavorite(channel, false)
    }

    private fun writeManualChannels(channels: List<IptvChannel>) {
        val array = JSONArray()
        channels.forEach { channel ->
            array.put(
                JSONObject()
                    .put("id", channel.id)
                    .put("name", channel.name)
                    .put("streamUrl", channel.streamUrl)
                    .put("packageId", channel.group)
                    .put("logoUrl", channel.logoUrl),
            )
        }
        prefs.edit().putString(KEY_MANUAL_CHANNELS, array.toString()).apply()
    }

    companion object {
        private const val KEY_CUSTOM = "custom"
        private const val KEY_ENABLED_PACKAGES = "enabledPackages"
        private const val KEY_FAVORITES = "favoriteChannels"
        private const val KEY_MANUAL_CHANNELS = "manualChannels"
        private const val MAX_MANUAL_CHANNELS = 100
        private const val CUSTOM_ID = "custom"
        val FREE_TV = IptvSource.m3u(
            id = "free-tv-us",
            name = "Free-TV USA",
            playlistUrl = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_usa.m3u8",
            builtIn = true,
        )
        val IPTV_ORG_US = IptvSource.m3u(
            id = "iptv-org-us",
            name = "IPTV-org USA",
            playlistUrl = "https://iptv-org.github.io/iptv/countries/us.m3u",
            builtIn = true,
        )
        private val BUILT_INS = listOf(FREE_TV, IPTV_ORG_US)

        fun custom(
            name: String,
            type: IptvSourceType,
            endpoint: String,
            username: String,
            password: String,
        ): IptvSource = IptvSource(CUSTOM_ID, name.ifBlank { "My TV" }, type, endpoint, username, password)
    }
}

internal object IptvSessionCatalog {
    @Volatile
    var snapshot: IptvSessionSnapshot = IptvSessionSnapshot()
}
