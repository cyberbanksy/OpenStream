package com.orgista.openstream.iptv

object IptvPresentationPolicy {
    private val providerNoise = Regex("(?i)\\b(?:iptv[ -]?org|free[ -]?tv|usa?|united states|hd|sd|fhd|uhd|4k)\\b")
    private val separators = Regex("\\s*[;|/:]+\\s*")

    fun sourceName(value: String): String = when {
        value.contains("iptv-org", ignoreCase = true) -> "Live TV"
        value.contains("free-tv", ignoreCase = true) -> "Live TV"
        else -> value.trim().ifBlank { "Live TV" }
    }

    fun category(value: String): String {
        val parts = value
            .split(separators)
            .map { it.replace(providerNoise, " ").replace(Regex("\\s+"), " ").trim(' ', '-', '_') }
            .filter { it.length > 1 && !it.equals("x", ignoreCase = true) }
        val joined = parts.joinToString(" ").trim()
        val normalized = joined.lowercase()
        val standard = when {
            "movie" in normalized || "cinema" in normalized -> "Movies"
            "news" in normalized -> "News"
            "sport" in normalized -> "Sports"
            "kid" in normalized || "children" in normalized || "family" in normalized -> "Kids & Family"
            "music" in normalized -> "Music"
            "document" in normalized -> "Documentaries"
            "weather" in normalized -> "Weather"
            "local" in normalized -> "Local"
            "entertain" in normalized -> "Entertainment"
            "relig" in normalized || "faith" in normalized -> "Faith"
            "shop" in normalized -> "Shopping"
            else -> null
        }
        return standard ?: joined.ifBlank { "Other" }.split(' ').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar(Char::titlecase)
        }
    }

    fun category(channel: IptvChannel): String = category(channel.group)
}
