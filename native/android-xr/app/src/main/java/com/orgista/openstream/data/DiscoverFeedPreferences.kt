package com.orgista.openstream.data

import org.json.JSONArray
import org.json.JSONObject

enum class DiscoverFeedGroup(val displayName: String, val description: String) {
    UP_NEXT("Up Next", "Resume playback and see the titles or channel uploads most likely to interest you."),
    LIBRARY("Library", "Show rows built from your connected libraries and device media."),
    ADD_ONS("Add-Ons", "Show catalog and recommendation rows supplied by enabled add-ons."),
    YOUTUBE("YouTube", "Show recent uploads from enabled YouTube channel sources."),
}

data class DiscoverFeedPreferences(
    val order: List<DiscoverFeedGroup>,
    val visibleGroups: Set<DiscoverFeedGroup>,
) {
    fun withVisibility(group: DiscoverFeedGroup, visible: Boolean): DiscoverFeedPreferences = copy(
        visibleGroups = if (visible) visibleGroups + group else visibleGroups - group,
    )

    fun move(group: DiscoverFeedGroup, direction: Int): DiscoverFeedPreferences {
        val current = order.indexOf(group)
        if (current < 0) return this
        val target = (current + direction.coerceIn(-1, 1)).coerceIn(order.indices)
        if (target == current) return this
        val changed = order.toMutableList()
        changed[current] = changed[target]
        changed[target] = group
        return copy(order = changed)
    }

    companion object {
        val DEFAULT = DiscoverFeedPreferences(
            order = listOf(
                DiscoverFeedGroup.UP_NEXT,
                DiscoverFeedGroup.LIBRARY,
                DiscoverFeedGroup.ADD_ONS,
                DiscoverFeedGroup.YOUTUBE,
            ),
            visibleGroups = DiscoverFeedGroup.entries.toSet(),
        )
    }
}

object DiscoverFeedPreferencesCodec {
    fun encode(preferences: DiscoverFeedPreferences): String = JSONObject()
        .put("order", JSONArray(preferences.order.map(DiscoverFeedGroup::name)))
        .put("hidden", JSONArray(DiscoverFeedGroup.entries.filterNot(preferences.visibleGroups::contains).map(DiscoverFeedGroup::name)))
        .toString()

    fun decode(raw: String): DiscoverFeedPreferences = runCatching {
        val json = JSONObject(raw)
        val storedOrder = enumValues(json.optJSONArray("order"))
        val order = (storedOrder + DiscoverFeedGroup.entries).distinct()
        val hidden = enumValues(json.optJSONArray("hidden")).toSet()
        DiscoverFeedPreferences(order, DiscoverFeedGroup.entries.toSet() - hidden)
    }.getOrDefault(DiscoverFeedPreferences.DEFAULT)

    private fun enumValues(array: JSONArray?): List<DiscoverFeedGroup> = if (array == null) {
        emptyList()
    } else {
        (0 until array.length()).mapNotNull { index ->
            runCatching { DiscoverFeedGroup.valueOf(array.optString(index)) }.getOrNull()
        }
    }
}
