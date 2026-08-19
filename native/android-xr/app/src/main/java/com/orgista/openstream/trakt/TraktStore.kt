package com.orgista.openstream.trakt

import android.content.Context

data class TraktAppCredentials(val clientId: String, val clientSecret: String)
data class TraktTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)

/** App-private, backup-disabled Trakt OAuth state. Values are never logged. */
class TraktStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openstream.trakt.v1", Context.MODE_PRIVATE)

    val credentials: TraktAppCredentials?
        get() {
            val id = prefs.getString(KEY_CLIENT_ID, null).orEmpty()
            val secret = prefs.getString(KEY_CLIENT_SECRET, null).orEmpty()
            return TraktAppCredentials(id, secret).takeIf { id.isNotBlank() && secret.isNotBlank() }
        }

    val tokens: TraktTokens?
        get() {
            val access = prefs.getString(KEY_ACCESS, null).orEmpty()
            val refresh = prefs.getString(KEY_REFRESH, null).orEmpty()
            return TraktTokens(access, refresh, prefs.getLong(KEY_EXPIRES, 0L)).takeIf { access.isNotBlank() }
        }

    var syncEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    fun saveCredentials(clientId: String, clientSecret: String) {
        prefs.edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun saveTokens(tokens: TraktTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES, tokens.expiresAtMillis)
            .apply()
    }

    fun disconnect() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).apply()
    }

    companion object {
        private const val KEY_CLIENT_ID = "clientId"
        private const val KEY_CLIENT_SECRET = "clientSecret"
        private const val KEY_ACCESS = "accessToken"
        private const val KEY_REFRESH = "refreshToken"
        private const val KEY_EXPIRES = "expiresAt"
        private const val KEY_ENABLED = "syncEnabled"
    }
}
