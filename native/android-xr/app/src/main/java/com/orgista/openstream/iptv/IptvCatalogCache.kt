package com.orgista.openstream.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object IptvCatalogPolicy {
    fun initialChannels(cached: List<IptvChannel>): List<IptvChannel> = cached

    fun afterRefresh(cached: List<IptvChannel>, fresh: List<IptvChannel>): List<IptvChannel> =
        fresh.ifEmpty { cached }

    fun mergeRoutes(channels: List<IptvChannel>): List<IptvChannel> {
        val merged = linkedMapOf<String, IptvChannel>()
        channels.forEach { channel ->
            val canonical = canonicalName(channel.name)
            // Three- and four-letter station names (CNN, CBS, ESPN) are common.
            // Keeping them source-scoped left dead public cards ahead of healthy
            // configured-provider routes. Two-letter names remain too ambiguous.
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

    /**
     * A configured account is the user's preferred lineup. IPTV-org is the
     * maintained public catalog; Free-TV remains useful as a bounded backup.
     */
    fun mergeForPlayback(channels: List<IptvChannel>): List<IptvChannel> =
        mergeRoutes(
            channels.withIndex()
                .sortedWith(compareBy<IndexedValue<IptvChannel>> { routePriority(it.value) }.thenBy { it.index })
                .map(IndexedValue<IptvChannel>::value),
        )

    private fun routePriority(channel: IptvChannel): Int = when (channel.sourceId) {
        "custom", ManualLiveChannelPolicy.SOURCE_ID -> 0
        "iptv-org-us" -> 1
        "free-tv-us" -> 2
        else -> 1
    }

    private fun canonicalName(value: String): String = value
        .lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace("24/7", " ")
        .replace(Regex("\\b(?:live|tv|television|channel|hd|sd|fhd|uhd|4k|east|west)\\b"), " ")
        .filter(Char::isLetterOrDigit)
}

/** JSON codec for the public built-in catalog only; private account routes are never cached here. */
object IptvCatalogCodec {
    private const val VERSION = 1
    private const val MAX_CACHE_CHARS = 2_000_000
    private const val MAX_CHANNELS = 2_000

    fun encode(sourceId: String, channels: List<IptvChannel>): String = JSONObject()
        .put("version", VERSION)
        .put("sourceId", sourceId)
        .put(
            "channels",
            JSONArray().apply {
                channels.take(MAX_CHANNELS).forEach { channel ->
                    put(
                        JSONObject()
                            .put("id", channel.id)
                            .put("sourceId", channel.sourceId)
                            .put("sourceName", channel.sourceName)
                            .put("name", channel.name)
                            .put("group", channel.group)
                            .put("logoUrl", channel.logoUrl)
                            .put("streamUrl", channel.streamUrl)
                            .put("fallbackStreamUrls", JSONArray(channel.fallbackStreamUrls))
                            .put("httpUserAgent", channel.httpUserAgent)
                            .put("httpReferer", channel.httpReferer)
                            .put("guideId", channel.guideId)
                            .put("channelNumber", channel.channelNumber),
                    )
                }
            },
        )
        .toString()

    fun decode(expectedSourceId: String, raw: String): List<IptvChannel> {
        if (raw.length > MAX_CACHE_CHARS) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        if (root.optInt("version") != VERSION || root.optString("sourceId") != expectedSourceId) return emptyList()
        val values = root.optJSONArray("channels") ?: return emptyList()
        return (0 until minOf(values.length(), MAX_CHANNELS)).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val sourceId = value.optString("sourceId").take(80)
            val streamUrl = value.optString("streamUrl").take(4_096)
            if (sourceId != expectedSourceId || !IptvEndpointPolicy.isPlayableStream(streamUrl)) return@mapNotNull null
            IptvChannel(
                id = value.optString("id").take(160).ifBlank { return@mapNotNull null },
                sourceId = sourceId,
                sourceName = value.optString("sourceName").take(120).ifBlank { "Free-TV USA" },
                name = value.optString("name").take(200).ifBlank { return@mapNotNull null },
                group = value.optString("group").take(120).ifBlank { "USA" },
                logoUrl = value.optString("logoUrl").take(2_048),
                streamUrl = streamUrl,
                fallbackStreamUrls = value.optJSONArray("fallbackStreamUrls")?.let { fallbacks ->
                    (0 until minOf(fallbacks.length(), 11)).mapNotNull { fallbackIndex ->
                        fallbacks.optString(fallbackIndex).take(4_096)
                            .takeIf(IptvEndpointPolicy::isPlayableStream)
                    }
                }.orEmpty(),
                httpUserAgent = sanitizeHeader(value.optString("httpUserAgent")),
                httpReferer = sanitizeHeader(value.optString("httpReferer")),
                guideId = value.optString("guideId").take(240),
                channelNumber = value.optString("channelNumber").take(24),
            )
        }.distinctBy(IptvChannel::streamUrl)
    }

    private fun sanitizeHeader(value: String): String? = value.take(512)
        .takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
}

class IptvCatalogCache(context: Context) {
    private val directory = context.applicationContext.cacheDir

    private fun file(sourceId: String): File {
        val safeId = sourceId.lowercase().replace(Regex("[^a-z0-9-]"), "-").take(80)
        return File(directory, "iptv-$safeId-v1.json")
    }

    fun load(sourceId: String): List<IptvChannel> = runCatching {
        val sourceFile = file(sourceId)
        if (!sourceFile.isFile) emptyList() else IptvCatalogCodec.decode(sourceId, sourceFile.readText())
    }.getOrDefault(emptyList())

    fun save(sourceId: String, channels: List<IptvChannel>) {
        if (channels.isEmpty()) return
        runCatching {
            val sourceFile = file(sourceId)
            val temporary = File(sourceFile.parentFile, "${sourceFile.name}.tmp")
            temporary.writeText(IptvCatalogCodec.encode(sourceId, channels))
            if (!temporary.renameTo(sourceFile)) {
                sourceFile.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }
}
