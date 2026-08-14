package com.orgista.openstream.iptv

import java.net.URI

object IptvEndpointPolicy {
    fun normalize(value: String): String = value.trim().trimEnd('/')

    fun isAllowed(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        if (uri.userInfo != null || uri.host.isNullOrBlank() || uri.fragment != null) return false
        when (uri.scheme?.lowercase()) {
            "https" -> true
            // Many user-supplied Xtream and M3U services still publish only an
            // HTTP endpoint. Credentials stay out of the stored endpoint URL
            // and the UI calls out the transport limitation before saving.
            "http" -> true
            else -> false
        }
    }.getOrDefault(false)

    fun isPlayableStream(value: String): Boolean = runCatching {
        val uri = URI(value)
        when (uri.scheme?.lowercase()) {
            "https" -> uri.host?.isNotBlank() == true
            "http" -> uri.host?.isNotBlank() == true && uri.userInfo == null
            else -> false
        }
    }.getOrDefault(false)
}
