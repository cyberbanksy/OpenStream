package com.orgista.openstream.iptv

/**
 * The expensive, immutable part of the Live guide. Building this involves
 * route merging, package classification, program grouping, and normalizing
 * thousands of searchable strings. It belongs on a worker dispatcher and is
 * retained with the process-local session so navigation never repeats it.
 */
data class LivePreparedGuide(
    val allChannels: List<IptvChannel>,
    val activeChannels: List<IptvChannel>,
    val guideByChannel: Map<String, List<IptvGuideProgram>>,
    val guideSearchIndex: List<LiveGuideSearchEntry>,
    val fullCatalogSearchIndex: List<LiveChannelSearchEntry>,
)

object LivePreparedGuidePolicy {
    fun build(
        channels: List<IptvChannel>,
        programs: List<IptvGuideProgram>,
        enabledPackageIds: Set<String>,
        favoriteChannelKeys: Set<String>,
    ): LivePreparedGuide {
        val allChannels = IptvCatalogPolicy.mergeForPlayback(channels)
        val activeChannels = LiveChannelPackagePolicy.lineupChannels(
            channels = allChannels,
            enabledPackageIds = enabledPackageIds,
            favoriteChannelKeys = favoriteChannelKeys,
        )
        return LivePreparedGuide(
            allChannels = allChannels,
            activeChannels = activeChannels,
            guideByChannel = programs.groupBy(IptvGuideProgram::channelKey),
            guideSearchIndex = LiveGuideSearchIndex.build(activeChannels, programs),
            fullCatalogSearchIndex = LiveChannelPackagePolicy.buildSearchIndex(allChannels),
        )
    }
}

/** Pure wraparound navigation shared by touch gestures and controller buttons. */
object LiveChannelNavigationPolicy {
    fun adjacent(
        channels: List<IptvChannel>,
        currentChannelKey: String,
        direction: Int,
        currentChannelName: String = "",
    ): IptvChannel? {
        if (channels.isEmpty() || direction == 0) return null
        val keyIndex = channels.indexOfFirst {
            LiveChannelPackagePolicy.channelKey(it) == currentChannelKey
        }
        val currentIndex = if (keyIndex >= 0) {
            keyIndex
        } else {
            val identity = navigationIdentity(currentChannelName)
            channels.indexOfFirst { identity.isNotBlank() && navigationIdentity(it.name) == identity }
        }
        if (currentIndex < 0) return null
        return channels[Math.floorMod(currentIndex + direction.sign(), channels.size)]
    }

    private fun Int.sign(): Int = if (this > 0) 1 else -1

    private fun navigationIdentity(value: String): String = value
        .replace(Regex("(?i)\\([^)]*(?:east|west|sd|hd|fhd|uhd|4k|\\d{3,4}p)[^)]*\\)"), " ")
        .replace(Regex("(?i)\\b(?:east|west|sd|hd|fhd|uhd|4k|\\d{3,4}p)\\b"), " ")
        .lowercase()
        .filter(Char::isLetterOrDigit)
}
