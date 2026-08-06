package com.orgista.openstream.player

import android.content.Context
import com.orgista.openstream.domain.ViewingSignal
import java.security.MessageDigest

data class SavedPlayback(val positionMs: Long, val durationMs: Long)

object PlaybackProgressPolicy {
    private const val MIN_RESUME_MS = 15_000L
    private const val MIN_REMAINING_MS = 30_000L
    private const val COMPLETION_PERCENT = 95L

    fun resumePosition(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L || positionMs < MIN_RESUME_MS) return 0L
        if (positionMs >= durationMs - MIN_REMAINING_MS) return 0L
        if (positionMs * 100L / durationMs >= COMPLETION_PERCENT) return 0L
        return positionMs.coerceAtMost(durationMs)
    }

    fun progressPercent(positionMs: Long, durationMs: Long): Int? {
        if (resumePosition(positionMs, durationMs) == 0L) return null
        return (positionMs * 100L / durationMs).toInt().coerceIn(1, 94)
    }
}

/** Stores only hashed media identifiers, so NAS paths never appear in preferences. */
class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("openstream.playback", Context.MODE_PRIVATE)

    fun load(uri: String): SavedPlayback? {
        val key = key(uri)
        val duration = prefs.getLong("$key.duration", 0L)
        val position = prefs.getLong("$key.position", 0L)
        return SavedPlayback(position, duration).takeIf {
            PlaybackProgressPolicy.resumePosition(it.positionMs, it.durationMs) > 0L
        }
    }

    fun save(uri: String, positionMs: Long, durationMs: Long) {
        val key = key(uri)
        if (PlaybackProgressPolicy.resumePosition(positionMs, durationMs) == 0L) {
            prefs.edit().remove("$key.position").remove("$key.duration").apply()
            return
        }
        prefs.edit()
            .putLong("$key.position", positionMs)
            .putLong("$key.duration", durationMs)
            .apply()
    }

    fun clear(uri: String) {
        val key = key(uri)
        prefs.edit().remove("$key.position").remove("$key.duration").apply()
    }

    fun progressPercent(uri: String): Int? = load(uri)?.let {
        PlaybackProgressPolicy.progressPercent(it.positionMs, it.durationMs)
    }

    /** Record one user-initiated playback, keyed by a hash of the media id. */
    fun markStarted(mediaId: String, nowMs: Long = System.currentTimeMillis()) {
        val key = "history.${key(mediaId)}"
        prefs.edit()
            .putLong("$key.last", nowMs)
            .putInt("$key.count", prefs.getInt("$key.count", 0).plus(1).coerceAtMost(10_000))
            .putBoolean("$key.completed", false)
            .apply()
    }

    fun markCompleted(mediaId: String, completed: Boolean = true) {
        val key = "history.${key(mediaId)}"
        prefs.edit().putBoolean("$key.completed", completed).apply()
    }

    /**
     * Returns signals only for the supplied catalog ids. The preference keys
     * are hashes, so NAS folder/file names are never persisted in plaintext.
     */
    fun viewingSignals(mediaIds: Collection<String>): Map<String, ViewingSignal> = buildMap {
        mediaIds.forEach { mediaId ->
            val key = "history.${key(mediaId)}"
            val last = prefs.getLong("$key.last", 0L)
            val count = prefs.getInt("$key.count", 0)
            if (last > 0L && count > 0) {
                put(
                    mediaId,
                    ViewingSignal(
                        lastPlayedAtMs = last,
                        playCount = count,
                        completed = prefs.getBoolean("$key.completed", false),
                    ),
                )
            }
        }
    }

    private fun key(uri: String): String = MessageDigest.getInstance("SHA-256")
        .digest(uri.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
