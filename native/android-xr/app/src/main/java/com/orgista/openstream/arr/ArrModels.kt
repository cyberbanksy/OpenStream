package com.orgista.openstream.arr

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI

enum class ArrKind(val displayName: String, val defaultPort: Int) {
    RADARR("Radarr", 7878),
    SONARR("Sonarr", 8989),
}

enum class ArrDownloadDestination(val storedValue: String) {
    ASK("ask"),
    DEVICE("device"),
    SERVER("server");

    companion object {
        fun fromStored(value: String?): ArrDownloadDestination =
            entries.firstOrNull { it.storedValue == value } ?: ASK
    }
}

class ArrConnection(
    val kind: ArrKind,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val enabled: Boolean = true,
) {
    override fun toString(): String =
        "ArrConnection(kind=$kind,name=$name,baseUrl=$baseUrl,apiKey=<redacted>,enabled=$enabled)"
}

data class ArrStatus(val appName: String, val version: String, val instanceName: String)

object ArrEndpointPolicy {
    fun normalize(value: String): String = value.trim().trimEnd('/')

    fun isAllowed(value: String): Boolean = runCatching {
        val uri = URI(normalize(value))
        if (uri.userInfo != null || uri.host.isNullOrBlank() || uri.query != null || uri.fragment != null) return false
        when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> isPrivateLiteral(uri.host)
            else -> false
        }
    }.getOrDefault(false)

    fun isPrivateLiteral(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        if (!host.matches(Regex("(?:\\d{1,3}\\.){3}\\d{1,3}"))) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress
    }
}

class ArrStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openstream.arr.v1", Context.MODE_PRIVATE)

    fun load(kind: ArrKind): ArrConnection? {
        val raw = prefs.getString(kind.name.lowercase(), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ArrConnection(
                kind = kind,
                name = json.optString("name", kind.displayName),
                baseUrl = json.getString("baseUrl"),
                apiKey = json.getString("apiKey"),
                enabled = json.optBoolean("enabled", true),
            )
        }.getOrNull()
    }

    fun save(connection: ArrConnection) {
        require(ArrEndpointPolicy.isAllowed(connection.baseUrl)) { "Use HTTPS, or a private local IP address for HTTP." }
        require(connection.apiKey.isNotBlank()) { "API key is required." }
        val json = JSONObject()
            .put("name", connection.name)
            .put("baseUrl", ArrEndpointPolicy.normalize(connection.baseUrl))
            .put("apiKey", connection.apiKey)
            .put("enabled", connection.enabled)
        prefs.edit().putString(connection.kind.name.lowercase(), json.toString()).apply()
    }

    fun remove(kind: ArrKind) {
        prefs.edit().remove(kind.name.lowercase()).apply()
    }

    var downloadDestination: ArrDownloadDestination
        get() = ArrDownloadDestination.fromStored(prefs.getString("download_destination", null))
        set(value) {
            prefs.edit { putString("download_destination", value.storedValue) }
        }
}
