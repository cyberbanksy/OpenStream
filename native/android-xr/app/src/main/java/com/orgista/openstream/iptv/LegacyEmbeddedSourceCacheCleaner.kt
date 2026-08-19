package com.orgista.openstream.iptv

import android.content.Context

/** Upgrade cleanup for cache files created by the former compiled Live sources. */
object LegacyEmbeddedSourceCachePolicy {
    private val exactNames = setOf(
        "iptv-free-tv-us-v1.json",
        "iptv-iptv-org-us-v1.json",
    )
    private val guidePrefixes = listOf(
        "iptv-guide-epgtalk-us-guide-",
        "iptv-guide-free-tv-us-",
        "iptv-guide-iptv-org-",
    )

    fun shouldDelete(fileName: String): Boolean =
        fileName in exactNames || guidePrefixes.any(fileName::startsWith)
}

object LegacyEmbeddedSourceCacheCleaner {
    fun clean(context: Context): Int = listOf(context.cacheDir, context.filesDir)
        .flatMap { directory -> directory.listFiles()?.toList().orEmpty() }
        .count { file ->
            file.isFile && LegacyEmbeddedSourceCachePolicy.shouldDelete(file.name) && file.delete()
        }
}
