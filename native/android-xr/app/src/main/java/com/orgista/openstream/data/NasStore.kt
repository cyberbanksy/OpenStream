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
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                StremioAddon(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    transportUrl = json.getString("transportUrl"),
                    version = json.optString("version"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addStremio(addon: StremioAddon) {
        val array = JSONArray()
        (stremioAddons().filter { it.transportUrl != addon.transportUrl } + addon).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("transportUrl", entry.transportUrl)
                    .put("version", entry.version),
            )
        }
        prefs.edit().putString(KEY_STREMIO, array.toString()).apply()
    }

    fun removeStremio(id: String) {
        val array = JSONArray()
        stremioAddons().filter { it.id != id }.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("transportUrl", entry.transportUrl)
                    .put("version", entry.version),
            )
        }
        prefs.edit().putString(KEY_STREMIO, array.toString()).apply()
    }

    private companion object {
        const val KEY_NAS = "nas.v1"
        const val KEY_STREMIO = "stremio.v1"
    }
}
