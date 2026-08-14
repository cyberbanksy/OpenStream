package com.orgista.openstream.iptv

import kotlin.math.max
import kotlin.math.min

data class GuideSegment(val startFraction: Float, val widthFraction: Float)

data class LiveGuideSearchEntry(
    val channel: IptvChannel,
    val normalizedName: String,
    val channelText: String,
    val programText: String,
)

object LiveGuideSearchIndex {
    fun build(channels: List<IptvChannel>, programs: List<IptvGuideProgram>): List<LiveGuideSearchEntry> {
        val programsByChannel = programs.groupBy(IptvGuideProgram::channelKey)
        return channels.map { channel ->
            val key = LiveChannelPackagePolicy.channelKey(channel)
            LiveGuideSearchEntry(
                channel = channel,
                normalizedName = normalize(channel.name),
                channelText = normalize(
                    "${channel.name} ${channel.group} ${LiveChannelPackagePolicy.packageFor(channel).label}",
                ),
                programText = normalize(
                    programsByChannel[key].orEmpty().joinToString(" ") {
                        "${it.title} ${it.subtitle} ${it.description} ${it.category}"
                    },
                ),
            )
        }
    }

    fun search(index: List<LiveGuideSearchEntry>, query: String, limit: Int = 300): List<IptvChannel> {
        val needle = normalize(query)
        if (needle.isBlank()) return index.map(LiveGuideSearchEntry::channel).take(limit)
        val tokens = needle.split(' ').filter(String::isNotBlank)
        return index.asSequence()
            .mapNotNull { entry ->
                val score = when {
                    entry.normalizedName == needle -> 0
                    entry.normalizedName.startsWith(needle) -> 1
                    tokens.all(entry.normalizedName::contains) -> 2
                    entry.channelText.contains(needle) -> 3
                    tokens.all(entry.channelText::contains) -> 4
                    entry.programText.contains(needle) -> 5
                    tokens.all(entry.programText::contains) -> 6
                    else -> return@mapNotNull null
                }
                entry.channel to score
            }
            .sortedWith(
                compareBy<Pair<IptvChannel, Int>> { it.second }
                    .thenBy { channelNumberValue(it.first.channelNumber) }
                    .thenBy { it.first.name.lowercase() },
            )
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun channelNumberValue(value: String): Double = value.toDoubleOrNull() ?: Double.MAX_VALUE
}

object LiveGuidePolicy {
    const val HALF_HOUR_MILLIS = 30 * 60_000L
    const val WINDOW_MILLIS = 4 * HALF_HOUR_MILLIS

    fun windowStart(nowMillis: Long, halfHourOffset: Int): Long =
        nowMillis - Math.floorMod(nowMillis, HALF_HOUR_MILLIS) + halfHourOffset * HALF_HOUR_MILLIS

    fun programsFor(
        channel: IptvChannel,
        programs: List<IptvGuideProgram>,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): List<IptvGuideProgram> {
        val key = LiveChannelPackagePolicy.channelKey(channel)
        return programs.asSequence()
            .filter { it.channelKey == key && it.endMillis > windowStartMillis && it.startMillis < windowEndMillis }
            .sortedBy(IptvGuideProgram::startMillis)
            .toList()
    }

    fun currentProgram(
        channel: IptvChannel,
        programs: List<IptvGuideProgram>,
        nowMillis: Long,
    ): IptvGuideProgram? {
        val key = LiveChannelPackagePolicy.channelKey(channel)
        return programs.firstOrNull { it.channelKey == key && it.startMillis <= nowMillis && it.endMillis > nowMillis }
    }

    fun nextProgram(
        channel: IptvChannel,
        programs: List<IptvGuideProgram>,
        nowMillis: Long,
    ): IptvGuideProgram? {
        val key = LiveChannelPackagePolicy.channelKey(channel)
        return programs.asSequence()
            .filter { it.channelKey == key && it.startMillis >= nowMillis }
            .minByOrNull(IptvGuideProgram::startMillis)
    }

    fun matchesSearch(channel: IptvChannel, programs: List<IptvGuideProgram>, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return true
        val packageLabel = LiveChannelPackagePolicy.packageFor(channel).label
        if ("${channel.name} ${channel.group} $packageLabel".lowercase().contains(needle)) return true
        val key = LiveChannelPackagePolicy.channelKey(channel)
        return programs.any {
            it.channelKey == key && "${it.title} ${it.subtitle} ${it.description} ${it.category}".lowercase().contains(needle)
        }
    }

    /** Provider schedules win; the supplemental guide fills channels with no provider schedule. */
    fun fillMissingSchedules(
        primary: List<IptvGuideProgram>,
        fallback: List<IptvGuideProgram>,
    ): List<IptvGuideProgram> {
        val primaryKeys = primary.mapTo(hashSetOf(), IptvGuideProgram::channelKey)
        return primary + fallback.filter { it.channelKey !in primaryKeys }
    }

    fun segment(program: IptvGuideProgram, windowStartMillis: Long, windowEndMillis: Long): GuideSegment {
        val duration = (windowEndMillis - windowStartMillis).coerceAtLeast(1L).toFloat()
        val visibleStart = max(program.startMillis, windowStartMillis)
        val visibleEnd = min(program.endMillis, windowEndMillis)
        return GuideSegment(
            startFraction = ((visibleStart - windowStartMillis) / duration).coerceIn(0f, 1f),
            widthFraction = ((visibleEnd - visibleStart) / duration).coerceIn(0f, 1f),
        )
    }
}

object IptvGuideMatchPolicy {
    fun channelKey(
        guideChannelId: String,
        displayNames: List<String>,
        channels: List<IptvChannel>,
    ): String? = matcher(channels).channelKey(guideChannelId, displayNames)

    fun matcher(channels: List<IptvChannel>): Matcher = Matcher(channels)

    class Matcher(channels: List<IptvChannel>) {
        private data class ChannelMatch(
            val key: String,
            val normalizedName: String,
            val tokens: Set<String>,
        )

        private val matches = channels.map { channel ->
            ChannelMatch(
                key = LiveChannelPackagePolicy.channelKey(channel),
                normalizedName = normalizeName(channel.name),
                tokens = nameTokens(channel.name),
            )
        }
        private val guideIds = buildMap {
            channels.forEach { channel ->
                if (channel.guideId.isNotBlank()) putIfAbsent(normalizeId(channel.guideId), LiveChannelPackagePolicy.channelKey(channel))
            }
        }
        private val callSigns = buildMap {
            channels.forEach { channel ->
                stationCallSigns("${channel.name} ${channel.guideId}").forEach { sign ->
                    putIfAbsent(sign, LiveChannelPackagePolicy.channelKey(channel))
                }
            }
        }
        private val exactNames = buildMap {
            matches.forEach { match ->
                if (match.normalizedName.isNotBlank()) putIfAbsent(match.normalizedName, match.key)
            }
        }
        private val tokenIndex = buildMap<String, MutableList<ChannelMatch>> {
            matches.forEach { match ->
                match.tokens.forEach { token -> getOrPut(token) { mutableListOf() }.add(match) }
            }
        }

        fun channelKey(guideChannelId: String, displayNames: List<String>): String? {
            val normalizedGuideId = normalizeId(guideChannelId)
            guideIds[normalizedGuideId]?.let { return it }
            val guideCallSigns = (displayNames + guideChannelId).flatMap(::stationCallSigns).toSet()
            guideCallSigns.firstNotNullOfOrNull(callSigns::get)?.let { return it }
            val names = (displayNames + guideChannelId.replace('.', ' '))
                .map(::normalizeName)
                .filter { it.length >= 4 }
            names.firstNotNullOfOrNull(exactNames::get)?.let { return it }
            val guideTokenSets = displayNames.map(::nameTokens).filter { it.isNotEmpty() }
            val candidates = linkedSetOf<ChannelMatch>()
            guideTokenSets.forEach { guideTokens ->
                guideTokens.forEach { token -> tokenIndex[token]?.let(candidates::addAll) }
            }
            return candidates.asSequence()
                .map { match ->
                    val compactScore = names.maxOfOrNull { guideName ->
                        nameScore(match.normalizedName, guideName)
                    } ?: 0
                    val tokenScore = guideTokenSets.maxOfOrNull { guideTokens ->
                        tokenScore(match.tokens, guideTokens)
                    } ?: 0
                    val score = maxOf(compactScore, tokenScore)
                    match to score
                }
                .filter { it.second > 0 }
                .maxWithOrNull(
                    compareBy<Pair<ChannelMatch, Int>> { it.second }.thenBy { it.first.normalizedName.length },
                )
                ?.first
                ?.key
        }
    }

    private fun normalizeId(value: String): String = value.substringBefore('@').lowercase().filter(Char::isLetterOrDigit)

    private fun stationCallSigns(value: String): List<String> = Regex("(?i)\\b[kw][a-z]{3}\\b")
        .findAll(value)
        .map { it.value.uppercase() }
        .toList()

    private fun nameTokens(value: String): Set<String> = value
        .lowercase()
        .replace(Regex("\\b(?:dallas|dfw|fort[ -]?worth)\\b"), "dfw")
        .split(Regex("[^a-z0-9]+"))
        .filterTo(linkedSetOf()) { token ->
            token.length >= 3 && token !in setOf(
                "the", "live", "channel", "network", "east", "west", "news", "local",
                "fort", "worth",
            )
        }

    private fun tokenScore(channelTokens: Set<String>, guideTokens: Set<String>): Int {
        val networks = setOf("abc", "cbs", "fox", "nbc", "pbs", "cw", "ion")
        var sharedCount = 0
        var sharesNetwork = false
        channelTokens.forEach { token ->
            if (token in guideTokens) {
                sharedCount += 1
                if (token in networks) sharesNetwork = true
            }
        }
        return if (sharedCount >= 2 && sharesNetwork) 350 + sharedCount else 0
    }

    private fun normalizeName(value: String): String = value
        .lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\b(?:live|tv|television|channel|network|hd|sd|fhd|uhd|4k|east|west)\\b"), " ")
        .filter(Char::isLetterOrDigit)

    private fun nameScore(channelName: String, guideName: String): Int = when {
        channelName.isBlank() || guideName.isBlank() -> 0
        channelName == guideName -> 1000 + channelName.length
        minOf(channelName.length, guideName.length) >= 8 &&
            (channelName in guideName || guideName in channelName) -> 500 + minOf(channelName.length, guideName.length)
        else -> 0
    }
}

object IptvGuideSourcePolicy {
    private const val MAX_GUIDES = 2
    private val nationalUs = Regex("(?i)epg_ripper_US\\d+\\.xml")
    private val usSports = Regex("(?i)epg_ripper_US_SPORTS\\d+\\.xml")

    fun select(urls: List<String>, sourceName: String): List<String> {
        val safe = urls.filter(IptvEndpointPolicy::isAllowed).distinct()
        if (safe.size <= MAX_GUIDES) return safe
        if (sourceName.contains("USA", ignoreCase = true) || sourceName.contains("US", ignoreCase = true)) {
            val preferred = buildList {
                safe.firstOrNull(nationalUs::containsMatchIn)?.let(::add)
                safe.firstOrNull(usSports::containsMatchIn)?.let(::add)
            }
            if (preferred.isNotEmpty()) return preferred.distinct().take(MAX_GUIDES)
        }
        return safe.filterNot { it.contains("ALL_SOURCES", ignoreCase = true) }.take(MAX_GUIDES)
    }

    fun retryCandidates(url: String): List<String> {
        val values = mutableListOf(url)
        if (url.contains("epgshare01.online", ignoreCase = true)) {
            val alternate = when {
                Regex("(?i)_US1\\.xml").containsMatchIn(url) -> url.replace(Regex("(?i)_US1\\.xml"), "_US2.xml")
                Regex("(?i)_US2\\.xml").containsMatchIn(url) -> url.replace(Regex("(?i)_US2\\.xml"), "_US1.xml")
                else -> null
            }
            if (alternate != null) values += alternate
        }
        return values.distinct().take(2)
    }
}
