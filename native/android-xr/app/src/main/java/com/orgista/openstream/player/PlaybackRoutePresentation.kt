package com.orgista.openstream.player

object PlaybackRoutePresentation {
    fun compactLabel(videoHeight: Int?, uri: String, offline: Boolean): String {
        val quality = when {
            videoHeight != null && videoHeight >= 2000 -> "4K"
            videoHeight != null && videoHeight in 1..719 -> "SD"
            else -> "HD"
        }
        val route = when {
            offline -> "Offline"
            uri.startsWith("smb://") || uri.startsWith("file://") -> "Local"
            else -> "Stream"
        }
        return "$quality $route"
    }

    fun status(
        primaryStatus: String,
        activeRouteIndex: Int,
        routeCount: Int,
        selectedTrackLabels: List<String>,
    ): String {
        if (activeRouteIndex <= 0) return primaryStatus
        return buildString {
            append("Alternative ${activeRouteIndex + 1}/$routeCount")
            if (selectedTrackLabels.isNotEmpty()) {
                append(" · ")
                append(selectedTrackLabels.joinToString(" · "))
            }
        }
    }
}
