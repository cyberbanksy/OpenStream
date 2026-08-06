package com.orgista.openstream.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NasConnection(
    val id: String,
    val name: String,
    val host: String,
    val share: String,
    val username: String,
    val password: String,
)

data class StremioAddon(
    val id: String,
    val name: String,
    val transportUrl: String,
    val version: String,
    val manifestId: String = id,
    val resources: Set<String> = setOf("stream"),
    val catalogs: List<StremioCatalog> = emptyList(),
    val builtIn: Boolean = false,
    val enabled: Boolean = true,
)

data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String,
    val requiresInput: Boolean = false,
)

/**
 * Connection storage in app-private preferences. Credentials never leave the
 * app sandbox and disappear with app data — the same lifecycle as the kids
 * lock, so a reset wipes both together.
 */
class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("openstream.connections", Context.MODE_PRIVATE)

    fun nasConnections(): List<NasConnection> {
        val raw = prefs.getString(KEY_NAS, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                NasConnection(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    host = json.getString("host"),
                    share = json.getString("share"),
                    username = json.optString("username"),
                    password = json.optString("password"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addNas(name: String, host: String, share: String, username: String, password: String): NasConnection {
        val connection = NasConnection(UUID.randomUUID().toString(), name.ifBlank { share }, host, share, username, password)
        val array = JSONArray()
        (nasConnections() + connection).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("host", entry.host)
                    .put("share", entry.share)
                    .put("username", entry.username)
                    .put("password", entry.password),
            )
        }
        prefs.edit().putString(KEY_NAS, array.toString()).apply()
        return connection
    }

    fun removeNas(id: String) {
        val array = JSONArray()
        nasConnections().filter { it.id != id }.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("host", entry.host)
                    .put("share", entry.share)
                    .put("username", entry.username)
                    .put("password", entry.password),
            )
        }
        prefs.edit().putString(KEY_NAS, array.toString()).apply()
    }

    fun stremioAddons(): List<StremioAddon> {
        val raw = prefs.getString(KEY_STREMIO, "[]") ?: "[]"
        val custom = try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                val resources = json.optJSONArray("resources")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.toSet()
                }.orEmpty().ifEmpty { setOf("stream") }
                val catalogs = json.optJSONArray("catalogs")?.let { values ->
                    (0 until values.length()).mapNotNull { catalogIndex ->
                        val catalog = values.optJSONObject(catalogIndex) ?: return@mapNotNull null
                        val type = catalog.optString("type")
                        val id = catalog.optString("id")
                        if (type.isBlank() || id.isBlank()) return@mapNotNull null
                        StremioCatalog(
                            type = type,
                            id = id,
                            name = catalog.optString("name", id),
                            requiresInput = catalog.optBoolean("requiresInput"),
                        )
                    }
                }.orEmpty()
                StremioAddon(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    transportUrl = json.getString("transportUrl"),
                    version = json.optString("version"),
                    manifestId = json.optString("manifestId", json.getString("id")),
                    resources = resources,
                    catalogs = catalogs,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        val disabled = prefs.getStringSet(KEY_STREMIO_DISABLED, emptySet()).orEmpty()
        return (BuiltInStremioAddons.all + custom)
            .distinctBy { it.manifestId to it.transportUrl }
            .map { addon -> addon.copy(enabled = addon.id !in disabled) }
    }

    fun addStremio(addon: StremioAddon) {
        val array = JSONArray()
        (stremioAddons().filter { !it.builtIn && it.manifestId != addon.manifestId } + addon).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("transportUrl", entry.transportUrl)
                    .put("version", entry.version)
                    .put("manifestId", entry.manifestId)
                    .put("resources", JSONArray(entry.resources.toList()))
                    .put(
                        "catalogs",
                        JSONArray().apply {
                            entry.catalogs.forEach { catalog ->
                                put(
                                    JSONObject()
                                        .put("type", catalog.type)
                                        .put("id", catalog.id)
                                        .put("name", catalog.name)
                                        .put("requiresInput", catalog.requiresInput),
                                )
                            }
                        },
                    ),
            )
        }
        prefs.edit().putString(KEY_STREMIO, array.toString()).apply()
    }

    fun removeStremio(id: String) {
        if (BuiltInStremioAddons.all.any { it.id == id }) {
            setStremioEnabled(id, false)
            return
        }
        val array = JSONArray()
        stremioAddons().filter { !it.builtIn && it.id != id }.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("transportUrl", entry.transportUrl)
                    .put("version", entry.version)
                    .put("manifestId", entry.manifestId)
                    .put("resources", JSONArray(entry.resources.toList()))
                    .put(
                        "catalogs",
                        JSONArray().apply {
                            entry.catalogs.forEach { catalog ->
                                put(
                                    JSONObject()
                                        .put("type", catalog.type)
                                        .put("id", catalog.id)
                                        .put("name", catalog.name)
                                        .put("requiresInput", catalog.requiresInput),
                                )
                            }
                        },
                    ),
            )
        }
        prefs.edit().putString(KEY_STREMIO, array.toString()).apply()
    }

    fun setStremioEnabled(id: String, enabled: Boolean) {
        val disabled = prefs.getStringSet(KEY_STREMIO_DISABLED, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(id) else disabled.add(id)
        prefs.edit().putStringSet(KEY_STREMIO_DISABLED, disabled).apply()
    }

    private companion object {
        const val KEY_NAS = "nas.v1"
        const val KEY_STREMIO = "stremio.v1"
        const val KEY_STREMIO_DISABLED = "stremio.disabled.v1"
    }
}
