package com.orgista.openstream.lan

import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object LanSetupProtocol {
    fun isAllowedClient(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress

    fun parseForm(body: String): Map<String, String> = body
        .split('&')
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator < 0) return@mapNotNull null
            decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
        }
        .toMap()

    fun manifestUrlError(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "Enter the add-on manifest URL."
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return "This URL is not valid. Example: https://addon.example/manifest.json"
        val scheme = uri.scheme?.lowercase()
        if (scheme == null) {
            return "Add https:// at the beginning. Example: https://addon.example/manifest.json"
        }
        if (scheme == "http") {
            return "HTTP links are not supported. Use the add-on's HTTPS manifest URL."
        }
        if (scheme != "https") {
            return "$scheme:// links are not supported here. Paste the add-on's HTTPS manifest URL."
        }
        if (uri.host.isNullOrBlank()) {
            return "This URL is missing an add-on host. Example: https://addon.example/manifest.json"
        }
        if (!uri.userInfo.isNullOrBlank()) {
            return "URLs containing a username or password are not supported."
        }
        if (!uri.rawQuery.isNullOrBlank()) {
            return "URL query options are not supported. Paste the complete HTTPS manifest URL supplied by the add-on."
        }
        if (!uri.rawFragment.isNullOrBlank()) {
            return "URL fragments are not supported. Remove everything after # and try again."
        }
        val filename = uri.path.orEmpty().substringAfterLast('/').takeIf(String::isNotBlank)
        if (filename?.endsWith(".json", ignoreCase = true) == true &&
            !filename.equals("manifest.json", ignoreCase = true)
        ) {
            return "This URL points to $filename. Paste a manifest.json URL or the add-on's base URL."
        }
        return null
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
