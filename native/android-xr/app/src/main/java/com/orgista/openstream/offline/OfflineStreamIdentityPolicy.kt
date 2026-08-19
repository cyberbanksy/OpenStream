package com.orgista.openstream.offline

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.StremioStream

enum class OfflineIdentityResult { MATCH, UNKNOWN, MISMATCH }

/** Prevents an add-on from labeling one movie's bytes with another movie's metadata. */
object OfflineStreamIdentityPolicy {
    private val yearPattern = Regex("(?:19|20)\\d{2}")
    private val technicalTokens = setOf(
        "4k", "2160p", "1440p", "1080p", "720p", "576p", "480p", "uhd", "bluray", "brrip",
        "webrip", "webdl", "web", "remux", "hdr", "hdr10", "dv", "dolby", "vision", "atmos",
        "hevc", "h265", "h264", "x265", "x264", "av1", "avc", "aac", "dts", "truehd", "eac3",
        "ddp", "proper", "repack", "extended", "mkv", "mp4", "avi", "stream", "source",
    )
    private val stopTokens = setOf("a", "an", "and", "of", "the", "to", "in", "on", "part")

    fun isVerifiedMatch(item: MediaItem, stream: StremioStream): Boolean =
        classify(item, stream) == OfflineIdentityResult.MATCH

    fun classify(item: MediaItem, stream: StremioStream): OfflineIdentityResult {
        val evidence = stream.filename?.takeIf(String::isNotBlank) ?: stream.title
        val evidenceTokens = tokens(evidence)
        val meaningfulEvidence = evidenceTokens.filterNot { it in technicalTokens || it in stopTokens || yearPattern.matches(it) }
        if (meaningfulEvidence.isEmpty()) return OfflineIdentityResult.UNKNOWN

        val titleTokens = tokens(item.title).filterNot { it in stopTokens }.toSet()
        if (titleTokens.isEmpty()) return OfflineIdentityResult.UNKNOWN
        val overlap = titleTokens.intersect(meaningfulEvidence.toSet()).size
        val required = when (titleTokens.size) {
            1 -> 1
            2, 3 -> 2
            else -> 2
        }
        val years = yearPattern.findAll(evidence).map { it.value.toInt() }.toSet()
        val wrongYear = item.year > 0 && years.isNotEmpty() && item.year !in years
        if (overlap >= required && !wrongYear) return OfflineIdentityResult.MATCH

        // A provider filename is strong identity evidence. A quality-only title is not.
        return if (stream.filename != null && (meaningfulEvidence.size >= 2 || wrongYear)) {
            OfflineIdentityResult.MISMATCH
        } else {
            OfflineIdentityResult.UNKNOWN
        }
    }

    private fun tokens(value: String): List<String> = value
        .lowercase()
        .replace("spider-man", "spiderman")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 2 }
}
