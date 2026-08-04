package com.orgista.openstream.library

import com.orgista.openstream.Logs
import com.orgista.openstream.data.MediaCandidate
import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import com.orgista.openstream.data.NasConnection
import com.orgista.openstream.data.Smb
import com.orgista.openstream.data.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IndexedLibrary(val movies: List<MediaItem>, val shows: List<MediaItem>) {
    val all: List<MediaItem> get() = movies + shows
    val isEmpty: Boolean get() = movies.isEmpty() && shows.isEmpty()
}

/**
 * Scans a NAS folder, parses filenames, and auto-organizes into Movies and TV
 * Shows — grouping episodes under their series and seasons. When a TMDB key is
 * available it enriches each title with a poster, backdrop, overview, and
 * year; otherwise the parsed title/year stand on their own.
 */
object LibraryIndexer {
    suspend fun index(connection: NasConnection, folder: LibraryFolder, tmdb: Tmdb): IndexedLibrary =
        withContext(Dispatchers.IO) {
            val files = runCatching { Smb.walkVideos(connection, folder.path) }
                .onFailure { Logs.warn("library scan failed for ${folder.path}", it) }
                .getOrDefault(emptyList())
            Logs.event("library scan: ${files.size} video files under ${connection.host}/${connection.share}/${folder.path}")

            val movieCandidates = ArrayList<com.orgista.openstream.data.NasEntry>()
            val episodeFiles = ArrayList<Pair<com.orgista.openstream.data.NasEntry, ParsedTitle.Episode>>()

            for (file in files) {
                val parsed = FilenameParser.parse(file.name)
                val treatAsEpisode = parsed is ParsedTitle.Episode && folder.kind != LibraryKind.MOVIES
                if (treatAsEpisode) {
                    episodeFiles.add(file to parsed as ParsedTitle.Episode)
                } else {
                    movieCandidates.add(file)
                }
            }

            // Group movie files by their containing folder so a movie folder
            // (or a disc rip with many files) collapses into one title, named
            // from the folder — the cleanest source, like Infuse.
            val rootPath = folder.path.trim('/')
            val movies = movieCandidates
                .groupBy { entry -> movieFolderKey(entry.path, rootPath) }
                .map { (folderKey, group) ->
                    val main = group.maxByOrNull { it.sizeBytes } ?: group.first()
                    val parsed = if (folderKey.isNotBlank()) {
                        FilenameParser.parseMovieFolder(folderKey.substringAfterLast('/'))
                    } else {
                        FilenameParser.parse(main.name).let { if (it is ParsedTitle.Movie) it else ParsedTitle.Movie(it.cleanTitle, null) }
                    }
                    buildMovie(connection, folder, main, parsed, tmdb)
                }
                .sortedBy { it.title.lowercase() }

            val shows = episodeFiles
                .groupBy { it.second.cleanTitle.lowercase() }
                .map { (_, group) -> buildShow(connection, folder, group, tmdb) }
                .sortedBy { it.title.lowercase() }

            IndexedLibrary(movies, shows)
        }

    // The movie's identifying folder: the path segment directly under the
    // library root. Files sitting at the root have no folder (parse the file).
    // Disc structures (BDMV/VIDEO_TS) collapse to their movie folder.
    private fun movieFolderKey(entryPath: String, rootPath: String): String {
        val relative = entryPath.removePrefix("$rootPath/").removePrefix(rootPath).trim('/')
        val segments = relative.split('/')
        if (segments.size <= 1) return "" // file directly in the library root
        val discMarkers = setOf("bdmv", "stream", "video_ts", "playlist")
        // Use the shallowest non-disc folder as the movie's name.
        val meaningful = segments.dropLast(1).filter { it.lowercase() !in discMarkers }
        return meaningful.firstOrNull() ?: segments.first()
    }

    private fun candidate(connection: NasConnection, folder: LibraryFolder, entry: com.orgista.openstream.data.NasEntry): MediaCandidate {
        val container = entry.name.substringAfterLast('.').uppercase()
        val sizeLabel = if (entry.sizeBytes >= 1_000_000_000) {
            "%.1f GB".format(entry.sizeBytes / 1_000_000_000.0)
        } else if (entry.sizeBytes > 0) {
            "${entry.sizeBytes / 1_000_000} MB"
        } else {
            ""
        }
        return MediaCandidate(
            id = "nas-${folder.id}-${entry.path}",
            sourceName = connection.name,
            sourceKind = SourceKind.NAS,
            uri = Smb.fileUrl(connection, entry.path),
            formatLabel = listOf(container, sizeLabel).filter { it.isNotBlank() }.joinToString(" · "),
            directPlay = true,
        )
    }

    private fun buildMovie(
        connection: NasConnection,
        folder: LibraryFolder,
        entry: com.orgista.openstream.data.NasEntry,
        parsed: ParsedTitle.Movie,
        tmdb: Tmdb,
    ): MediaItem {
        val meta = runCatching { tmdb.searchMovie(parsed.cleanTitle, parsed.year) }.getOrNull()
        return MediaItem(
            id = "nas-movie-${folder.id}-${entry.path}",
            kind = MediaKind.MOVIE,
            title = meta?.title ?: parsed.cleanTitle,
            synopsis = meta?.overview?.ifBlank { "From ${connection.name}." } ?: "From ${connection.name}.",
            year = meta?.year ?: parsed.year ?: 0,
            rating = meta?.rating ?: "",
            runtimeMinutes = 0,
            posterUrl = Tmdb.posterUrl(meta?.posterPath),
            backdropUrl = Tmdb.backdropUrl(meta?.backdropPath),
            tmdbId = meta?.tmdbId,
            candidates = listOf(candidate(connection, folder, entry)),
        )
    }

    private fun buildShow(
        connection: NasConnection,
        folder: LibraryFolder,
        group: List<Pair<com.orgista.openstream.data.NasEntry, ParsedTitle.Episode>>,
        tmdb: Tmdb,
    ): MediaItem {
        val showTitle = group.first().second.cleanTitle
        val meta = runCatching { tmdb.searchShow(showTitle) }.getOrNull()
        val episodes = group
            .sortedWith(compareBy({ it.second.season }, { it.second.episode }))
            .map { (entry, parsed) ->
                MediaItem(
                    id = "nas-ep-${folder.id}-${entry.path}",
                    kind = MediaKind.SERIES,
                    title = meta?.title ?: showTitle,
                    subtitle = "S${parsed.season} E${parsed.episode}",
                    synopsis = "From ${connection.name}.",
                    year = meta?.year ?: 0,
                    rating = meta?.rating ?: "",
                    runtimeMinutes = 0,
                    candidates = listOf(candidate(connection, folder, entry)),
                )
            }
        val seasons = group.map { it.second.season }.distinct().size
        return MediaItem(
            id = "nas-show-${folder.id}-${showTitle.lowercase()}",
            kind = MediaKind.SERIES,
            title = meta?.title ?: showTitle,
            subtitle = "${episodes.size} episode${if (episodes.size == 1) "" else "s"} · $seasons season${if (seasons == 1) "" else "s"}",
            synopsis = meta?.overview?.ifBlank { "From ${connection.name}." } ?: "From ${connection.name}.",
            year = meta?.year ?: 0,
            rating = meta?.rating ?: "",
            runtimeMinutes = 0,
            posterUrl = Tmdb.posterUrl(meta?.posterPath),
            backdropUrl = Tmdb.backdropUrl(meta?.backdropPath),
            tmdbId = meta?.tmdbId,
            episodes = episodes,
            candidates = episodes.firstOrNull()?.candidates ?: emptyList(),
        )
    }
}
