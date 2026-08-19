package com.orgista.openstream.ui

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.SourceKind

object MediaCardPresentation {
    private val episodeSummary = Regex("(?i)^\\d+\\s+episodes?\\s*[·|]\\s*\\d+\\s+seasons?$")

    fun subtitle(item: MediaItem): String {
        val sourceKinds = item.candidates.map { it.sourceKind }.toSet()
        return when {
            SourceKind.OFFLINE in sourceKinds -> "Offline"
            SourceKind.NAS in sourceKinds -> "Local"
            item.subtitle?.matches(episodeSummary) == true -> item.subtitle
            item.subtitle != null && item.imdbId != null -> "Stream"
            item.subtitle != null -> item.subtitle
            item.year > 0 -> item.year.toString()
            else -> "Stream"
        }
    }
}
