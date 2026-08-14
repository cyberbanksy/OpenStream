package com.orgista.openstream.iptv

import java.net.URI

object M3uParser {
    private val attribute = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    fun parse(sourceId: String, playlistUrl: String, content: String, sourceName: String = sourceId): List<IptvChannel> {
        return parsePlaylist(sourceId, playlistUrl, content, sourceName).channels
    }

    fun parsePlaylist(
        sourceId: String,
        playlistUrl: String,
        content: String,
        sourceName: String = sourceId,
    ): IptvPlaylist {
        val lines = content.lineSequence().map(String::trim).toList()
        val channels = ArrayList<IptvChannel>()
        var index = 0
        while (index < lines.size && channels.size < 20_000) {
            val info = lines[index]
            if (!info.startsWith("#EXTINF", ignoreCase = true)) {
                index++
                continue
            }
            val attrs = attribute.findAll(info).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
            val fallbackName = info.substringAfterLast(',', "").trim()
            val name = attrs["tvg-name"].orEmpty().ifBlank { fallbackName }.ifBlank { "Untitled channel" }
            var httpUserAgent = sanitizeHeader(attrs["http-user-agent"])
            var httpReferer = sanitizeHeader(attrs["http-referrer"] ?: attrs["http-referer"])
            var urlIndex = index + 1
            while (urlIndex < lines.size && (lines[urlIndex].isBlank() || lines[urlIndex].startsWith('#'))) {
                if (lines[urlIndex].startsWith("#EXTINF", ignoreCase = true)) break
                val option = lines[urlIndex]
                when {
                    option.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) ->
                        httpUserAgent = sanitizeHeader(option.substringAfter('='))
                    option.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) ||
                        option.startsWith("#EXTVLCOPT:http-referer=", ignoreCase = true) ->
                        httpReferer = sanitizeHeader(option.substringAfter('='))
                }
                urlIndex++
            }
            val rawUrl = lines.getOrNull(urlIndex).orEmpty().takeUnless { it.startsWith('#') }.orEmpty()
            val resolved = if (rawUrl.isBlank()) {
                ""
            } else {
                runCatching { URI(playlistUrl).resolve(rawUrl).toString() }.getOrDefault("")
            }
            if (IptvEndpointPolicy.isPlayableStream(resolved)) {
                val providerId = attrs["tvg-id"].orEmpty()
                val stable = providerId.ifBlank { "$name|$resolved" }.hashCode().toUInt().toString(16)
                channels += IptvChannel(
                    id = "$sourceId-$stable",
                    sourceId = sourceId,
                    sourceName = sourceName,
                    name = name,
                    group = attrs["group-title"].orEmpty().ifBlank { "Other" },
                    logoUrl = attrs["tvg-logo"].orEmpty(),
                    streamUrl = resolved,
                    httpUserAgent = httpUserAgent,
                    httpReferer = httpReferer,
                    guideId = providerId,
                    channelNumber = attrs["tvg-chno"].orEmpty().take(24),
                )
            }
            index = if (rawUrl.isNotBlank() && urlIndex > index) urlIndex + 1 else index + 1
        }
        return IptvPlaylist(
            channels = channels.distinctBy { it.streamUrl },
            guideUrls = parseGuideUrls(lines.firstOrNull().orEmpty(), playlistUrl),
        )
    }

    private fun parseGuideUrls(header: String, playlistUrl: String): List<String> {
        if (!header.startsWith("#EXTM3U", ignoreCase = true)) return emptyList()
        val attrs = attribute.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val raw = attrs["x-tvg-url"] ?: attrs["url-tvg"] ?: return emptyList()
        return raw.split(',').asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { value -> runCatching { URI(playlistUrl).resolve(value).toString() }.getOrNull() }
            .filter(IptvEndpointPolicy::isAllowed)
            .distinct()
            .take(120)
            .toList()
    }

    private fun sanitizeHeader(value: String?): String? = value?.trim()?.take(512)
        ?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
}
