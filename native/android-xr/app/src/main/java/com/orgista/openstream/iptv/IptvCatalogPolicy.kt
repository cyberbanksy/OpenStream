package com.orgista.openstream.iptv

/**
 * Deduplicates routes returned by the viewer's configured provider and manual
 * Channel Manager entries. Input order is authoritative; OpenStream never
 * inserts or promotes a compiled provider.
 */
object IptvCatalogPolicy {
    fun mergeRoutes(channels: List<IptvChannel>): List<IptvChannel> {
        val merged = linkedMapOf<String, IptvChannel>()
        channels.forEach { channel ->
            val canonical = canonicalName(channel.name)
            // Three- and four-letter station names (CNN, CBS, ESPN) are common.
            // Two-letter names remain too ambiguous to merge automatically.
            val key = if (canonical.length >= 3) canonical else "${channel.sourceId}:${channel.id}"
            val existing = merged[key]
            merged[key] = if (existing == null) {
                channel
            } else {
                existing.copy(
                    fallbackStreamUrls = (
                        existing.fallbackStreamUrls + channel.streamUrl + channel.fallbackStreamUrls
                        ).filter { it != existing.streamUrl }.distinct().take(11),
                )
            }
        }
        return merged.values.toList()
    }

    fun mergeForPlayback(channels: List<IptvChannel>): List<IptvChannel> = mergeRoutes(channels)

    private fun canonicalName(value: String): String = value
        .lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace("24/7", " ")
        .replace(Regex("\\b(?:live|tv|television|channel|hd|sd|fhd|uhd|4k|east|west)\\b"), " ")
        .filter(Char::isLetterOrDigit)
}
