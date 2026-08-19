package com.orgista.openstream.iptv

import com.orgista.openstream.data.UserAgents
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

object XtreamClient {
    fun load(source: IptvSource): List<IptvChannel> {
        require(source.type == IptvSourceType.XTREAM)
        require(IptvEndpointPolicy.isAllowed(source.endpoint)) { "Enter an HTTP or HTTPS IPTV server address." }
        require(source.username.isNotBlank() && source.password.isNotBlank()) { "Username and password are required." }
        val categories = request(source, "get_live_categories")
        val categoryNames = parseCategories(categories)
        return parseChannels(source, request(source, "get_live_streams"), categoryNames)
    }

    fun parseChannels(
        source: IptvSource,
        body: String,
        categoryNames: Map<String, String> = emptyMap(),
    ): List<IptvChannel> {
        val array = JSONArray(body)
        return (0 until minOf(array.length(), 20_000)).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val streamId = json.optLong("stream_id", -1L).takeIf { it >= 0 } ?: return@mapNotNull null
            val extension = json.optString("container_extension", "m3u8")
                .lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "m3u8"
            val name = json.optString("name").trim().ifBlank { return@mapNotNull null }
            val categoryId = json.optString("category_id")
            val category = json.optString("category_name").ifBlank { categoryNames[categoryId] }.orEmpty().ifBlank { "Other" }
            if (category.equals("Events", ignoreCase = true) &&
                name.matches(Regex("(?i)^events?\\s*\\d*\\s*:?\\s*$"))
            ) return@mapNotNull null
            IptvChannel(
                id = "${source.id}-$streamId",
                sourceId = source.id,
                sourceName = source.name,
                name = name,
                group = category,
                logoUrl = json.optString("stream_icon"),
                streamUrl = streamUrl(source, streamId, extension),
                guideId = json.optString("epg_channel_id").trim()
                    .takeUnless { it.equals("null", ignoreCase = true) }
                    .orEmpty(),
                channelNumber = json.optString("num").trim()
                    .takeUnless { it.equals("null", ignoreCase = true) }
                    .orEmpty(),
            )
        }
    }

    private fun parseCategories(body: String): Map<String, String> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("category_id")
            val name = json.optString("category_name")
            if (id.isBlank() || name.isBlank()) null else id to name
        }.toMap()
    }

    private fun request(source: IptvSource, action: String): String {
        val base = IptvEndpointPolicy.normalize(source.endpoint)
        val query = "username=${encode(source.username)}&password=${encode(source.password)}&action=$action"
        val url = URI("$base/player_api.php?$query").toURL()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", UserAgents.product)
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "IPTV server returned HTTP $status." }
            return connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                require(text.length <= 12_000_000) { "IPTV response is too large." }
                text
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun streamUrl(source: IptvSource, streamId: Long, extension: String): String =
        "${IptvEndpointPolicy.normalize(source.endpoint)}/live/${encode(source.username)}/${encode(source.password)}/$streamId.$extension"

    fun xmlTvUrl(source: IptvSource): String =
        "${IptvEndpointPolicy.normalize(source.endpoint)}/xmltv.php?username=${encode(source.username)}&password=${encode(source.password)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
