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
    fun context(connection: NasConnection): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "15000")
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
}
