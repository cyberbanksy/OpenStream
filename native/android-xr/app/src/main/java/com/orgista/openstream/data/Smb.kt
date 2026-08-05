package com.orgista.openstream.data

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Properties

data class NasEntry(val name: String, val path: String, val isDirectory: Boolean, val sizeBytes: Long)

private val videoExtensions = setOf("mp4", "m4v", "mov", "mkv", "webm", "ts", "m2ts", "avi", "wmv")

fun isVideoFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in videoExtensions

/**
 * Direct SMB2/3 access to mounted-share-style NAS libraries — no gateway or
 * computer in the middle. All calls must run off the main thread.
 */
object Smb {
    // One authenticated CIFS context per connection. Building a context opens
    // a fresh connection pool, so reusing it across ExoPlayer's many seeks
    // (each a close+open) avoids re-negotiating and re-authenticating SMB on
    // every read — the difference between stalling and smooth streaming.
    private val contextCache = java.util.concurrent.ConcurrentHashMap<String, CIFSContext>()

    fun context(connection: NasConnection): CIFSContext =
        contextCache.getOrPut("${connection.host}|${connection.share}|${connection.username}") {
            buildContext(connection)
        }

    private fun buildContext(connection: NasConnection): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            // Streaming throughput: skip DFS lookups and use large transfer
            // buffers so ExoPlayer fills its buffer in few round trips.
            setProperty("jcifs.smb.client.dfs.disabled", "true")
            setProperty("jcifs.smb.client.rcv_buf_size", "1048576")
            setProperty("jcifs.smb.client.snd_buf_size", "1048576")
        }
        val base = BaseContext(PropertyConfiguration(properties))
        return if (connection.username.isNotBlank()) {
            base.withCredentials(NtlmPasswordAuthenticator("", connection.username, connection.password))
        } else {
            base.withAnonymousCredentials()
        }
    }

    fun shareUrl(connection: NasConnection, relativePath: String = ""): String {
        val cleanPath = relativePath.trim('/')
        val suffix = if (cleanPath.isEmpty()) "" else "$cleanPath/"
        return "smb://${connection.host}/${connection.share.trim('/')}/$suffix"
    }

    fun fileUrl(connection: NasConnection, relativePath: String): String =
        "smb://${connection.host}/${connection.share.trim('/')}/${relativePath.trim('/')}"

    /** Lists one directory: subdirectories plus video files, sorted by name. */
    fun list(connection: NasConnection, relativePath: String): List<NasEntry> {
        val directory = SmbFile(shareUrl(connection, relativePath), context(connection))
        val entries = directory.listFiles() ?: return emptyList()
        return entries.mapNotNull { file ->
            val name = file.name.trimEnd('/')
            if (name.startsWith(".")) return@mapNotNull null
            val isDirectory = file.isDirectory
            if (!isDirectory && !isVideoFileName(name)) return@mapNotNull null
            NasEntry(
                name = name,
                path = if (relativePath.isEmpty()) name else "${relativePath.trim('/')}/$name",
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0 else file.length(),
            )
        }.sortedWith(compareByDescending<NasEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** Connects and lists the share root, throwing with a readable message on failure. */
    fun validate(connection: NasConnection) {
        SmbFile(shareUrl(connection), context(connection)).use { root ->
            if (!root.exists()) throw IllegalStateException("The share was reachable but does not exist.")
        }
    }

    /**
     * Recursively collects video files under a share path for library
     * indexing. Bounded in depth and count so a huge share can't hang the
     * scan. Runs off the main thread.
     */
    fun walkVideos(connection: NasConnection, relativePath: String = "", maxDepth: Int = 6, limit: Int = 5000): List<NasEntry> {
        val results = ArrayList<NasEntry>()
        fun recurse(path: String, depth: Int) {
            if (depth > maxDepth || results.size >= limit) return
            val directory = SmbFile(shareUrl(connection, path), context(connection))
            val children = directory.listFiles() ?: return
            for (file in children) {
                if (results.size >= limit) return
                val name = file.name.trimEnd('/')
                if (name.startsWith(".")) continue
                val childPath = if (path.isEmpty()) name else "${path.trim('/')}/$name"
                if (file.isDirectory) {
                    recurse(childPath, depth + 1)
                } else if (isVideoFileName(name)) {
                    results.add(NasEntry(name, childPath, false, file.length()))
                }
            }
        }
        recurse(relativePath, 0)
        return results
    }

    data class SidecarSubtitle(val uri: String, val mimeType: String, val language: String?, val label: String)

    private val subtitleMimeTypes = mapOf(
        "srt" to "application/x-subrip",
        "vtt" to "text/vtt",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
    )

    /**
     * Finds subtitle files sitting next to a video (e.g. Movie.en.srt) so they
     * can be side-loaded — the same convention Infuse/Jellyfin use. Language is
     * guessed from the token before the extension.
     */
    fun siblingSubtitles(connection: NasConnection, videoRelativePath: String): List<SidecarSubtitle> {
        val dir = videoRelativePath.substringBeforeLast('/', "")
        val videoStem = videoRelativePath.substringAfterLast('/').substringBeforeLast('.')
        val directory = SmbFile(shareUrl(connection, dir), context(connection))
        val entries = directory.listFiles() ?: return emptyList()
        return entries.mapNotNull { file ->
            val name = file.name.trimEnd('/')
            val ext = name.substringAfterLast('.', "").lowercase()
            val mime = subtitleMimeTypes[ext] ?: return@mapNotNull null
            val stem = name.substringBeforeLast('.')
            // Match subs whose name starts with the video's name (allowing a
            // language suffix), or the generic single-sub case.
            if (!stem.startsWith(videoStem, ignoreCase = true) && entries.count { it.name.endsWith(".$ext") } > 1) {
                return@mapNotNull null
            }
            val langToken = stem.removePrefix(videoStem).trim('.', '-', '_', ' ').lowercase().ifBlank { null }
            val path = if (dir.isEmpty()) name else "${dir.trim('/')}/$name"
            SidecarSubtitle(
                uri = fileUrl(connection, path),
                mimeType = mime,
                language = langToken?.take(8),
                label = (langToken?.replaceFirstChar { it.uppercase() } ?: "Subtitles") + " (${ext.uppercase()})",
            )
        }.sortedBy { it.label }
    }

    /** True when a failure means the named share does not exist on the server. */
    fun isMissingShareError(error: Throwable?): Boolean {
        val message = error?.message?.lowercase() ?: return false
        return "network name" in message || "cannot be found" in message || "0xc00000cc" in message
    }

    /**
     * Turns raw jcifs/SMB exceptions into plain, actionable guidance. The
     * `share` argument names what the user tried to reach so the message can
     * be specific.
     */
    fun friendlyError(error: Throwable?, share: String): String {
        val message = error?.message ?: return "The NAS share could not be reached."
        val lower = message.lowercase()
        return when {
            isMissingShareError(error) ->
                "No share named “$share” on this server. Tap “List shares” and pick one."
            "access is denied" in lower || "logon failure" in lower || "0xc000006d" in lower ->
                "The username or password was rejected for this share."
            "failed to connect" in lower || "connection refused" in lower || "timed out" in lower ||
                "econnrefused" in lower || "unreachable" in lower ->
                "Couldn’t reach the server. Check it’s powered on and on the same Wi‑Fi."
            "unknown host" in lower ->
                "That host name could not be found on the network."
            else -> message
        }
    }
}
