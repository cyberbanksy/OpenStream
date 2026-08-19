package com.orgista.openstream.arr

import org.json.JSONArray
import org.json.JSONObject

/** Selects the strongest target/cutoff exposed by a user's ARR instance. */
object ArrQualityProfilePolicy {
    fun selectHighestProfileId(profiles: JSONArray): Int? =
        (0 until profiles.length())
            .mapNotNull(profiles::optJSONObject)
            .filter { it.optInt("id") > 0 }
            .maxByOrNull(::profileScore)
            ?.optInt("id")

    private fun profileScore(profile: JSONObject): Long {
        val items = profile.optJSONArray("items") ?: JSONArray()
        val cutoff = profile.optInt("cutoff", -1)
        val requestedQuality = maxOf(
            qualityLabelScore(profile.optString("name")),
            findCutoffScore(items, cutoff) ?: 0,
        )
        val strongestAllowed = itemScores(items, requireAllowed = true).maxOrNull() ?: 0
        return requestedQuality.toLong() * 1_000_000L + strongestAllowed
    }

    private fun findCutoffScore(items: JSONArray, cutoff: Int): Int? {
        if (cutoff < 0) return null
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val quality = item.optJSONObject("quality")
            if (quality?.optInt("id") == cutoff || item.optInt("id") == cutoff) {
                return itemScore(item)
            }
            findCutoffScore(item.optJSONArray("items") ?: JSONArray(), cutoff)?.let { return it }
        }
        return null
    }

    private fun itemScores(items: JSONArray, requireAllowed: Boolean): List<Int> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (requireAllowed && !item.optBoolean("allowed", true)) continue
            item.optJSONObject("quality")?.let { add(qualityScore(it)) }
            addAll(itemScores(item.optJSONArray("items") ?: JSONArray(), requireAllowed))
        }
    }

    private fun itemScore(item: JSONObject): Int = maxOf(
        item.optJSONObject("quality")?.let(::qualityScore) ?: 0,
        itemScores(item.optJSONArray("items") ?: JSONArray(), requireAllowed = false).maxOrNull() ?: 0,
    )

    private fun qualityScore(quality: JSONObject): Int {
        val label = buildString {
            append(quality.optString("name"))
            append(' ')
            append(quality.optString("source"))
        }
        val resolution = quality.optInt("resolution").takeIf { it > 0 }
            ?: resolutionFrom(label)
        return resolution * 100 + sourceScore(label)
    }

    private fun qualityLabelScore(label: String): Int =
        resolutionFrom(label) * 100 + sourceScore(label)

    private fun resolutionFrom(label: String): Int {
        val normalized = label.lowercase()
        return when {
            "2160" in normalized || "4k" in normalized || "ultra-hd" in normalized || "uhd" in normalized -> 2160
            "1080" in normalized -> 1080
            "720" in normalized -> 720
            "576" in normalized -> 576
            "480" in normalized || "sd" in normalized -> 480
            else -> 0
        }
    }

    private fun sourceScore(label: String): Int {
        val normalized = label.lowercase()
        return when {
            "remux" in normalized || "blurayraw" in normalized -> 40
            "bluray" in normalized || "blu-ray" in normalized -> 30
            "web" in normalized -> 20
            "hdtv" in normalized -> 10
            else -> 0
        }
    }
}
