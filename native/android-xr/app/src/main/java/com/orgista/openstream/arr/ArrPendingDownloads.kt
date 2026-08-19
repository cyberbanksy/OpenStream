package com.orgista.openstream.arr

import android.content.Context
import androidx.core.content.edit
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

data class PendingArrDownload(
    val kind: ArrKind,
    val target: ArrDownloadTarget,
    val queuedAtEpochMillis: Long,
)

object ArrPendingDownloadPolicy {
    fun key(value: PendingArrDownload): String = buildString {
        append(value.kind.name).append('|')
        append(value.target.tmdbId ?: 0).append('|')
        append(value.target.imdbId.orEmpty().lowercase()).append('|')
        append(value.target.title.lowercase()).append('|')
        append(value.target.year).append('|')
        append(value.target.seasonNumber ?: 0).append('|')
        append(value.target.episodeNumber ?: 0)
    }

    fun upsert(existing: List<PendingArrDownload>, value: PendingArrDownload): List<PendingArrDownload> =
        (existing.filterNot { key(it) == key(value) } + value).takeLast(100)

    fun shouldQueue(failure: Throwable): Boolean {
        if (failure !is IOException) return false
        val messages = buildList {
            var current: Throwable? = failure
            repeat(12) {
                val value = current ?: return@repeat
                value.message?.let(::add)
                current = value.cause
            }
        }.joinToString(" ")
        val status = Regex("(?i)HTTP\\s+(\\d{3})").find(messages)?.groupValues?.get(1)?.toIntOrNull()
        return status == null || status >= 500
    }
}

object ArrPendingDownloadCodec {
    fun encode(values: List<PendingArrDownload>): String = JSONArray().apply {
        values.takeLast(100).forEach { value ->
            put(
                JSONObject()
                    .put("kind", value.kind.name)
                    .put("queuedAt", value.queuedAtEpochMillis)
                    .put(
                        "target",
                        JSONObject()
                            .put("title", value.target.title)
                            .put("year", value.target.year)
                            .put("tmdbId", value.target.tmdbId)
                            .put("imdbId", value.target.imdbId)
                            .put("season", value.target.seasonNumber)
                            .put("episode", value.target.episodeNumber),
                    ),
            )
        }
    }.toString()

    fun decode(raw: String): List<PendingArrDownload> = runCatching {
        val values = JSONArray(raw)
        (0 until minOf(values.length(), 100)).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val target = value.optJSONObject("target") ?: return@mapNotNull null
            PendingArrDownload(
                kind = runCatching { ArrKind.valueOf(value.getString("kind")) }.getOrNull()
                    ?: return@mapNotNull null,
                target = ArrDownloadTarget(
                    title = target.optString("title").take(200).ifBlank { return@mapNotNull null },
                    year = target.optInt("year"),
                    tmdbId = target.optInt("tmdbId").takeIf { it > 0 },
                    imdbId = target.optString("imdbId").take(24).takeIf(String::isNotBlank),
                    seasonNumber = target.optInt("season").takeIf { it > 0 },
                    episodeNumber = target.optInt("episode").takeIf { it > 0 },
                ),
                queuedAtEpochMillis = value.optLong("queuedAt"),
            )
        }
    }.getOrDefault(emptyList())
}

class ArrPendingDownloadStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openstream.arr.pending.v1", Context.MODE_PRIVATE)

    fun load(): List<PendingArrDownload> = ArrPendingDownloadCodec.decode(prefs.getString(KEY, "[]").orEmpty())

    fun add(value: PendingArrDownload) {
        prefs.edit { putString(KEY, ArrPendingDownloadCodec.encode(ArrPendingDownloadPolicy.upsert(load(), value))) }
    }

    fun replace(values: List<PendingArrDownload>) {
        prefs.edit { putString(KEY, ArrPendingDownloadCodec.encode(values)) }
    }

    companion object { private const val KEY = "requests" }
}

data class ArrPendingFlushResult(val sent: Int, val remaining: Int, val destinations: Set<ArrKind>)

object ArrPendingDownloadRuntime {
    fun flush(context: Context): ArrPendingFlushResult {
        val pendingStore = ArrPendingDownloadStore(context)
        val connectionStore = ArrStore(context)
        val remaining = ArrayList<PendingArrDownload>()
        val destinations = linkedSetOf<ArrKind>()
        var sent = 0
        pendingStore.load().forEach { pending ->
            val connection = connectionStore.load(pending.kind)?.takeIf(ArrConnection::enabled)
            if (connection == null) {
                remaining += pending
                return@forEach
            }
            runCatching { ArrDownloadClient.enqueue(connection, pending.target) }
                .onSuccess {
                    sent++
                    destinations += pending.kind
                }
                .onFailure { remaining += pending }
        }
        pendingStore.replace(remaining)
        return ArrPendingFlushResult(sent, remaining.size, destinations)
    }
}
