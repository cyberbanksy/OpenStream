package com.orgista.openstream.library

/** What a filename parsed to: a movie, or one episode of a series. */
sealed interface ParsedTitle {
    val cleanTitle: String

    data class Movie(override val cleanTitle: String, val year: Int?) : ParsedTitle
    data class Episode(
        override val cleanTitle: String,
        val season: Int,
        val episode: Int,
    ) : ParsedTitle
}

/**
 * Offline filename parsing — the same idea Infuse/Jellyfin use to turn scene
 * filenames into clean titles and to sort movies from TV. No network needed;
 * metadata enrichment (posters/overviews) happens separately when a TMDB key
 * is present.
 */
object FilenameParser {
    private val seasonEpisode = Regex("""[sS](\d{1,2})[\s._-]*[eE](\d{1,2})""")
    private val episodeAltXFormat = Regex("""\b(\d{1,2})x(\d{2})\b""")
    private val yearPattern = Regex("""(?:^|[^\d])((?:19|20)\d{2})(?:[^\d]|$)""")

    // Scene tags stripped from titles once the title boundary is found.
    private val noiseTokens = setOf(
        "1080p", "2160p", "720p", "480p", "4k", "uhd", "hdr", "hdr10", "dv",
        "dolby", "vision", "atmos", "bluray", "blu-ray", "brrip", "bdrip",
        "webrip", "web-dl", "webdl", "web", "hdrip", "dvdrip", "remux",
        "x264", "x265", "h264", "h265", "hevc", "avc", "aac", "ac3", "eac3",
        "dts", "truehd", "flac", "10bit", "8bit", "hdtv", "proper", "repack",
        "internal", "limited", "extended", "unrated", "directors", "cut",
        "imax", "hmax", "amzn", "nf", "dsnp", "hulu", "multi", "dual",
    )

    fun parse(fileName: String): ParsedTitle {
        val base = fileName.substringBeforeLast('.').replace('.', ' ').replace('_', ' ').trim()

        seasonEpisode.find(base)?.let { match ->
            val title = cleanUpTo(base, match.range.first)
            return ParsedTitle.Episode(title, match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
        episodeAltXFormat.find(base)?.let { match ->
            val title = cleanUpTo(base, match.range.first)
            return ParsedTitle.Episode(title, match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }

        // Prefer the LAST year token: in "1992 (2024)" the release year is
        // 2024 and the title is "1992", not the other way around.
        val yearMatch = yearPattern.findAll(base).lastOrNull()
        if (yearMatch != null) {
            val boundary = yearMatch.range.first
            val title = cleanUpTo(base, boundary)
            return ParsedTitle.Movie(title.ifBlank { titleFrom(base) }, yearMatch.groupValues[1].toInt())
        }
        return ParsedTitle.Movie(titleFrom(base), null)
    }

    /**
     * Parses a title from a movie *folder* name (e.g. "A Man Called Otto
     * (2024)"). Infuse-style: the folder is usually the cleanest source, and
     * it collapses disc rips (BDMV/VIDEO_TS with many files) into one title.
     */
    fun parseMovieFolder(folderName: String): ParsedTitle.Movie {
        val cleaned = folderName.replace('.', ' ').replace('_', ' ').trim()
        val yearMatch = yearPattern.findAll(cleaned).lastOrNull()
        return if (yearMatch != null) {
            ParsedTitle.Movie(cleanUpTo(cleaned, yearMatch.range.first).ifBlank { titleFrom(cleaned) }, yearMatch.groupValues[1].toInt())
        } else {
            ParsedTitle.Movie(titleFrom(cleaned), null)
        }
    }

    /** Take everything before [boundary], then trim scene noise and separators. */
    private fun cleanUpTo(base: String, boundary: Int): String =
        titleFrom(base.substring(0, boundary.coerceAtLeast(0)))

    private fun titleFrom(raw: String): String {
        val words = raw.trim().trim('-', '(', '[', ' ').split(Regex("""\s+"""))
        val kept = ArrayList<String>()
        for (word in words) {
            val lower = word.lowercase().trim('(', ')', '[', ']', '-')
            if (lower.isEmpty()) continue
            if (lower in noiseTokens) break // noise marks the end of the real title
            kept.add(word.trim('(', ')', '[', ']'))
        }
        return kept.joinToString(" ").trim().ifBlank { raw.trim() }
    }
}
