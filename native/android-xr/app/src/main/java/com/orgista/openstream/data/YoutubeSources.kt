package com.orgista.openstream.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class YoutubeChannelSource(
    val id: String,
    val channelId: String,
    val name: String,
    val canonicalUrl: String,
    val thumbnailUrl: String?,
    val enabled: Boolean = true,
)

data class YoutubeChannelVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val publishedAt: String,
)

data class YoutubeChannelVideos(
    val channelTitle: String,
    val videos: List<YoutubeChannelVideo>,
)

/** Exact-channel verification without general YouTube search or API credentials. */
object YoutubeChannelResolver {
    private val channelIdPattern = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val channelUrlInPage = Regex("https://www\\.youtube\\.com/channel/(UC[A-Za-z0-9_-]{22})")
    private val canonicalChannelPage = Regex(
        """<link\s+rel=["']canonical["']\s+href=["']https://www\.youtube\.com/(?:@[A-Za-z0-9._-]{3,30}|channel/UC[A-Za-z0-9_-]{22}|user/[A-Za-z0-9._-]+|c/[A-Za-z0-9._-]+)(?:/[^"']*)?["']""",
        RegexOption.IGNORE_CASE,
    )
    private val externalChannelId = Regex(
        """(?:["']externalId["']\s*:\s*["']|externalId\\x22:\\x22)(UC[A-Za-z0-9_-]{22})""",
    )

    fun validChannelId(value: String): Boolean = channelIdPattern.matches(value.trim())

    fun lookupUrl(input: String): String {
        val value = input.trim()
        require(value.length in 2..160) { "Enter a channel name, @handle, channel ID, or YouTube URL" }
        if (validChannelId(value)) return canonicalUrl(value)

        val urlCandidate = if (
            value.startsWith("youtube.com/", ignoreCase = true) ||
            value.startsWith("www.youtube.com/", ignoreCase = true)
        ) {
            "https://$value"
        } else {
            value
        }
        if (urlCandidate.startsWith("https://") || urlCandidate.startsWith("http://")) {
            return lookupUrlFromYoutubeUrl(urlCandidate)
        }
        val handle = if (value.startsWith('@')) value.drop(1) else value.replace(Regex("\\s+"), "")
        return handleUrl(handle)
    }

    fun parseVerifiedPage(html: String): YoutubeChannelSource {
        val directId = channelUrlInPage.find(html)?.groupValues?.getOrNull(1).orEmpty()
        val channelId = directId.ifBlank {
            if (canonicalChannelPage.containsMatchIn(html)) {
                externalChannelId.find(html)?.groupValues?.getOrNull(1).orEmpty()
            } else {
                ""
            }
        }
        require(validChannelId(channelId)) { "That exact YouTube channel could not be verified" }
        val title = metaContent(html, "og:title").let(::decodeHtml).ifBlank { "YouTube Channel" }
        val image = metaContent(html, "og:image").let(::decodeHtml).takeIf(::isTrustedChannelImage)
        return YoutubeChannelSource(
            id = "youtube:$channelId",
            channelId = channelId,
            name = title,
            canonicalUrl = canonicalUrl(channelId),
            thumbnailUrl = image,
        )
    }

    private fun lookupUrlFromYoutubeUrl(input: String): String {
        val uri = runCatching { URI(input) }.getOrElse {
            throw IllegalArgumentException("Use a valid YouTube channel URL", it)
        }
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        require(host == "youtube.com" || host.endsWith(".youtube.com")) { "Use a YouTube channel URL" }
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val first = parts.getOrNull(0).orEmpty()
        val second = parts.getOrNull(1).orEmpty()
        return when {
            first == "channel" && validChannelId(second) -> canonicalUrl(second)
            first.startsWith('@') -> handleUrl(first.drop(1))
            (first == "user" || first == "c") && second.isNotBlank() -> legacyUrl(first, second)
            else -> throw IllegalArgumentException("Use a YouTube channel URL, not a video or playlist URL")
        }
    }

    private fun handleUrl(handle: String): String {
        val value = handle.trim()
        require(value.length in 3..30 && !Regex("[\\s/?#@]").containsMatchIn(value)) {
            "Use an exact channel name or its unique @handle"
        }
        return "https://www.youtube.com/@${encodePathSegment(value)}"
    }

    private fun legacyUrl(type: String, name: String): String {
        val value = name.trim()
        require(value.isNotEmpty() && value.length <= 100 && !Regex("[\\s/?#]").containsMatchIn(value)) {
            "Use a valid YouTube channel URL"
        }
        return "https://www.youtube.com/$type/${encodePathSegment(value)}"
    }

    private fun canonicalUrl(channelId: String): String = "https://www.youtube.com/channel/$channelId"

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun metaContent(html: String, property: String): String {
        val tag = Regex("""<meta\s+[^>]*property=["']${Regex.escape(property)}["'][^>]*>""", RegexOption.IGNORE_CASE)
            .find(html)?.value.orEmpty()
        return Regex("""content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)

    private fun isTrustedChannelImage(value: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        uri.scheme == "https" && (
            host == "yt3.ggpht.com" || host.endsWith(".ytimg.com") || host.endsWith(".googleusercontent.com")
        )
    }.getOrDefault(false)
}

/** Secure parser for YouTube's small public Atom channel feed. */
object YoutubeChannelFeed {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private const val MAX_FEED_CHARS = 1_000_000
    private const val MAX_VIDEOS = 24

    fun feedUrl(channelId: String): String {
        val value = channelId.trim()
        require(YoutubeChannelResolver.validChannelId(value)) { "Use a verified YouTube channel ID" }
        return "https://www.youtube.com/feeds/videos.xml?channel_id=$value"
    }

    fun parse(xml: String, expectedChannelId: String): YoutubeChannelVideos {
        val channelId = expectedChannelId.trim()
        require(YoutubeChannelResolver.validChannelId(channelId)) { "Use a verified YouTube channel ID" }
        require(xml.isNotEmpty() && xml.length <= MAX_FEED_CHARS) { "YouTube returned an invalid channel feed" }
        val declarationScan = xml.uppercase(Locale.ROOT)
        require("<!DOCTYPE" !in declarationScan && "<!ENTITY" !in declarationScan) {
            "YouTube returned an invalid channel feed"
        }
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
                setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
            }
            val root = builder.parse(InputSource(StringReader(xml))).documentElement
            val feedChannelId = firstDirectChildText(root, "channelId")
            require(feedChannelId == channelId || feedChannelId == channelId.removePrefix("UC")) {
                "YouTube returned a different channel feed"
            }
            val channelTitle = firstDirectChildText(root, "title").ifBlank { "YouTube Channel" }
            val videos = ArrayList<YoutubeChannelVideo>()
            val seen = HashSet<String>()
            val entries = root.getElementsByTagNameNS("*", "entry")
            for (index in 0 until entries.length) {
                if (videos.size >= MAX_VIDEOS) break
                val entry = entries.item(index) as? Element ?: continue
                val entryChannelId = firstText(entry, "channelId")
                val videoId = firstText(entry, "videoId")
                if (entryChannelId != channelId || !videoIdPattern.matches(videoId) || !seen.add(videoId)) continue
                val title = firstText(entry, "title").ifBlank { "YouTube Video" }
                val suppliedThumbnail = firstAttribute(entry, "thumbnail", "url")
                val thumbnail = suppliedThumbnail.takeIf(::isTrustedVideoThumbnail)
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                videos += YoutubeChannelVideo(
                    videoId = videoId,
                    title = title,
                    thumbnailUrl = thumbnail,
                    publishedAt = firstText(entry, "published"),
                )
            }
            return YoutubeChannelVideos(channelTitle, videos)
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("YouTube returned an invalid channel feed", error)
        }
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun firstDirectChildText(parent: Element, localName: String): String {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && child.localName == localName) return child.textContent.orEmpty().trim()
            child = child.nextSibling
        }
        return ""
    }

    private fun firstText(parent: Element, localName: String): String =
        parent.getElementsByTagNameNS("*", localName).item(0)?.textContent.orEmpty().trim()

    private fun firstAttribute(parent: Element, localName: String, attribute: String): String =
        (parent.getElementsByTagNameNS("*", localName).item(0) as? Element)?.getAttribute(attribute).orEmpty().trim()

    private fun isTrustedVideoThumbnail(value: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        uri.scheme == "https" && (host == "i.ytimg.com" || Regex("i[0-9]+\\.ytimg\\.com").matches(host))
    }.getOrDefault(false)
}

/** Fixed-network client; all returned identities are revalidated before storage or display. */
object YoutubeChannelClient {
    fun resolve(input: String): YoutubeChannelSource {
        val url = YoutubeChannelResolver.lookupUrl(input)
        return try {
            get(url, "text/html,application/xhtml+xml", MAX_PAGE_CHARS) { status, body ->
                when (status) {
                    404 -> throw IllegalArgumentException("That exact YouTube channel was not found. Try its unique @handle.")
                    429 -> throw IllegalStateException("YouTube is temporarily limiting channel verification. Try again shortly.")
                }
                require(status in 200..299) { "OpenStream could not verify this YouTube channel." }
                YoutubeChannelResolver.parseVerifiedPage(body)
            }
        } catch (error: IOException) {
            throw IllegalStateException("YouTube could not be reached. Check this device's connection and try again.", error)
        }
    }

    fun recentVideos(channel: YoutubeChannelSource): YoutubeChannelVideos {
        val url = YoutubeChannelFeed.feedUrl(channel.channelId)
        return try {
            get(url, "application/atom+xml,application/xml,text/xml", MAX_FEED_CHARS) { status, body ->
                when (status) {
                    404 -> throw IllegalStateException("This channel's recent videos are unavailable.")
                    429 -> throw IllegalStateException("YouTube is temporarily limiting channel browsing.")
                }
                require(status in 200..299) { "OpenStream could not load this channel's recent videos." }
                YoutubeChannelFeed.parse(body, channel.channelId)
            }
        } catch (error: IOException) {
            throw IllegalStateException("YouTube could not be reached. Check this device's connection and try again.", error)
        }
    }

    private fun <T> get(url: String, accept: String, limit: Int, transform: (Int, String) -> T): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("User-Agent", WEB_USER_AGENT)
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            transform(status, readBounded(stream, limit))
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: InputStream?, limit: Int): String {
        if (stream == null) return ""
        val result = StringBuilder()
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                result.append(line)
                require(result.length <= limit) { "YouTube returned an unexpectedly large response." }
            }
        }
        return result.toString()
    }

    private const val MAX_PAGE_CHARS = 2_000_000
    private const val MAX_FEED_CHARS = 1_000_000
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
}

object YoutubeSourcePolicy {
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun section(channel: YoutubeChannelSource, videos: List<YoutubeChannelVideo>): AddonCatalogSection =
        AddonCatalogSection(
            addonId = channel.id,
            addonName = "YouTube",
            catalogId = "youtube:${channel.channelId}",
            catalogName = channel.name,
            items = videos.distinctBy { it.videoId }.map { video ->
                MediaItem(
                    id = "youtube:${video.videoId}",
                    kind = MediaKind.VIDEO,
                    title = video.title,
                    subtitle = channel.name,
                    synopsis = "Recent upload from ${channel.name}",
                    year = video.publishedAt.take(4).toIntOrNull() ?: 0,
                    rating = "",
                    runtimeMinutes = 0,
                    posterUrl = video.thumbnailUrl,
                    backdropUrl = video.thumbnailUrl,
                    trailerYoutubeKey = video.videoId,
                )
            },
        )

    fun videoId(item: MediaItem): String? = item.id.removePrefix("youtube:")
        .takeIf { item.kind == MediaKind.VIDEO && item.id.startsWith("youtube:") && videoIdPattern.matches(it) }
}

object YoutubeChannelSourceCodec {
    fun encode(sources: List<YoutubeChannelSource>): String = JSONArray().apply {
        sources.distinctBy { it.channelId }.forEach { source ->
            put(
                JSONObject()
                    .put("id", source.id)
                    .put("channelId", source.channelId)
                    .put("name", source.name)
                    .put("canonicalUrl", source.canonicalUrl)
                    .putOpt("thumbnailUrl", source.thumbnailUrl)
                    .put("enabled", source.enabled),
            )
        }
    }.toString()

    fun decode(raw: String): List<YoutubeChannelSource> = try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val channelId = json.optString("channelId").trim()
            if (!YoutubeChannelResolver.validChannelId(channelId)) return@mapNotNull null
            YoutubeChannelSource(
                id = json.optString("id").ifBlank { "youtube:$channelId" },
                channelId = channelId,
                name = json.optString("name").ifBlank { "YouTube Channel" },
                canonicalUrl = json.optString("canonicalUrl").ifBlank {
                    "https://www.youtube.com/channel/$channelId"
                },
                thumbnailUrl = json.optString("thumbnailUrl").takeIf(String::isNotBlank),
                enabled = json.optBoolean("enabled", true),
            )
        }.distinctBy { it.channelId }
    } catch (_: Exception) {
        emptyList()
    }
}
