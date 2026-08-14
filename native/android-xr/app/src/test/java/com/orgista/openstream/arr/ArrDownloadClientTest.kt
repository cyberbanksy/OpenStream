package com.orgista.openstream.arr

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread

class ArrDownloadClientTest {
    @Test
    fun `existing movie starts one Radarr movie search`() {
        ScriptedArrServer(
            """[{"tmdbId":603,"imdbId":"tt0133093","title":"The Matrix"}]""",
            """[{"id":41,"tmdbId":603,"title":"The Matrix"}]""",
            """{"id":9,"name":"MoviesSearch"}""",
        ).use { server ->
            val result = ArrDownloadClient.enqueue(
                server.connection(ArrKind.RADARR),
                ArrDownloadTarget(
                    title = "The Matrix",
                    year = 1999,
                    tmdbId = 603,
                    imdbId = "tt0133093",
                ),
            )

            server.awaitRequests(3)
            assertEquals("Radarr is searching for The Matrix", result.message)
            assertEquals("GET /api/v3/movie/lookup?term=tmdb%3A603", server.requests[0].requestLine)
            assertEquals("GET /api/v3/movie?tmdbId=603", server.requests[1].requestLine)
            assertEquals("POST /api/v3/command", server.requests[2].requestLine)
            val command = JSONObject(server.requests[2].body)
            assertEquals("MoviesSearch", command.getString("name"))
            assertEquals(41, command.getJSONArray("movieIds").getInt(0))
        }
    }

    @Test
    fun `new movie uses Radarr defaults and requests a search`() {
        ScriptedArrServer(
            """[{"tmdbId":603,"imdbId":"tt0133093","title":"The Matrix"}]""",
            "[]",
            """[{"id":3,"path":"/movies","accessible":true}]""",
            """[{"id":6,"name":"Any"}]""",
            """{"id":41,"title":"The Matrix"}""",
        ).use { server ->
            ArrDownloadClient.enqueue(
                server.connection(ArrKind.RADARR),
                ArrDownloadTarget("The Matrix", 1999, tmdbId = 603, imdbId = "tt0133093"),
            )

            server.awaitRequests(5)
            assertEquals("POST /api/v3/movie", server.requests[4].requestLine)
            val movie = JSONObject(server.requests[4].body)
            assertEquals("/movies", movie.getString("rootFolderPath"))
            assertEquals(6, movie.getInt("qualityProfileId"))
            assertTrue(movie.getBoolean("monitored"))
            assertTrue(movie.getJSONObject("addOptions").getBoolean("searchForMovie"))
        }
    }

    @Test
    fun `existing movie is moved to the strongest Radarr quality profile before search`() {
        ScriptedArrServer(
            """[{"tmdbId":603,"imdbId":"tt0133093","title":"The Matrix"}]""",
            """[{"id":41,"tmdbId":603,"title":"The Matrix","qualityProfileId":2}]""",
            """[{"id":2,"name":"HD-1080p"},{"id":9,"name":"Remux-2160p"}]""",
            """{"id":41,"tmdbId":603,"title":"The Matrix","qualityProfileId":9}""",
            """{"id":9,"name":"MoviesSearch"}""",
        ).use { server ->
            ArrDownloadClient.enqueue(
                server.connection(ArrKind.RADARR),
                ArrDownloadTarget("The Matrix", 1999, tmdbId = 603, imdbId = "tt0133093"),
            )

            server.awaitRequests(5)
            assertEquals("GET /api/v3/qualityprofile", server.requests[2].requestLine)
            assertEquals("PUT /api/v3/movie/41?moveFiles=false", server.requests[3].requestLine)
            assertEquals(9, JSONObject(server.requests[3].body).getInt("qualityProfileId"))
            assertEquals("POST /api/v3/command", server.requests[4].requestLine)
        }
    }

    @Test
    fun `season target starts one Sonarr season search`() {
        ScriptedArrServer(
            """[{"tvdbId":121361,"imdbId":"tt0944947","title":"Game of Thrones"}]""",
            """[{"id":17,"tvdbId":121361,"title":"Game of Thrones"}]""",
            """{"id":4,"name":"SeasonSearch"}""",
        ).use { server ->
            val result = ArrDownloadClient.enqueue(
                server.connection(ArrKind.SONARR),
                ArrDownloadTarget(
                    title = "Game of Thrones",
                    year = 2011,
                    imdbId = "tt0944947",
                    seasonNumber = 3,
                ),
            )

            server.awaitRequests(3)
            assertEquals("Sonarr is searching for Game of Thrones · Season 3", result.message)
            val command = JSONObject(server.requests[2].body)
            assertEquals("SeasonSearch", command.getString("name"))
            assertEquals(17, command.getInt("seriesId"))
            assertEquals(3, command.getInt("seasonNumber"))
        }
    }

    @Test
    fun `new series is added with Sonarr defaults before the selected season search`() {
        ScriptedArrServer(
            """[{"tvdbId":121361,"imdbId":"tt0944947","title":"Game of Thrones"}]""",
            "[]",
            """[{"id":2,"path":"/television","accessible":true}]""",
            """[{"id":8,"name":"Any"}]""",
            """{"id":17,"tvdbId":121361,"title":"Game of Thrones"}""",
            """{"id":4,"name":"SeasonSearch"}""",
        ).use { server ->
            ArrDownloadClient.enqueue(
                server.connection(ArrKind.SONARR),
                ArrDownloadTarget(
                    title = "Game of Thrones",
                    year = 2011,
                    imdbId = "tt0944947",
                    seasonNumber = 3,
                ),
            )

            server.awaitRequests(6)
            assertEquals("POST /api/v3/series", server.requests[4].requestLine)
            val series = JSONObject(server.requests[4].body)
            assertEquals("/television", series.getString("rootFolderPath"))
            assertEquals(8, series.getInt("qualityProfileId"))
            assertEquals("none", series.getJSONObject("addOptions").getString("monitor"))
            assertEquals("POST /api/v3/command", server.requests[5].requestLine)
            assertEquals("SeasonSearch", JSONObject(server.requests[5].body).getString("name"))
        }
    }

    @Test
    fun `existing series is moved to the strongest Sonarr quality profile before search`() {
        ScriptedArrServer(
            """[{"tvdbId":121361,"imdbId":"tt0944947","title":"Game of Thrones"}]""",
            """[{"id":17,"tvdbId":121361,"title":"Game of Thrones","qualityProfileId":2}]""",
            """[{"id":2,"name":"HD-1080p"},{"id":9,"name":"Remux-2160p"}]""",
            """{"id":17,"tvdbId":121361,"title":"Game of Thrones","qualityProfileId":9}""",
            """{"id":4,"name":"SeasonSearch"}""",
        ).use { server ->
            ArrDownloadClient.enqueue(
                server.connection(ArrKind.SONARR),
                ArrDownloadTarget(
                    title = "Game of Thrones",
                    year = 2011,
                    imdbId = "tt0944947",
                    seasonNumber = 3,
                ),
            )

            server.awaitRequests(5)
            assertEquals("GET /api/v3/qualityprofile", server.requests[2].requestLine)
            assertEquals("PUT /api/v3/series/17?moveFiles=false", server.requests[3].requestLine)
            assertEquals(9, JSONObject(server.requests[3].body).getInt("qualityProfileId"))
            assertEquals("POST /api/v3/command", server.requests[4].requestLine)
        }
    }

    @Test
    fun `episode target resolves the Sonarr episode id before searching`() {
        ScriptedArrServer(
            """[{"tvdbId":121361,"imdbId":"tt0944947","title":"Game of Thrones"}]""",
            """[{"id":17,"tvdbId":121361,"title":"Game of Thrones"}]""",
            """[{"id":81,"seasonNumber":3,"episodeNumber":2},{"id":82,"seasonNumber":3,"episodeNumber":3}]""",
            """{"id":5,"name":"EpisodeSearch"}""",
        ).use { server ->
            ArrDownloadClient.enqueue(
                server.connection(ArrKind.SONARR),
                ArrDownloadTarget(
                    title = "Game of Thrones",
                    year = 2011,
                    imdbId = "tt0944947",
                    seasonNumber = 3,
                    episodeNumber = 2,
                ),
            )

            server.awaitRequests(4)
            assertEquals("GET /api/v3/episode?seriesId=17&seasonNumber=3", server.requests[2].requestLine)
            val command = JSONObject(server.requests[3].body)
            assertEquals("EpisodeSearch", command.getString("name"))
            assertEquals(81, command.getJSONArray("episodeIds").getInt(0))
        }
    }
}

private data class CapturedArrRequest(val requestLine: String, val body: String)

private class ScriptedArrServer(vararg responseBodies: String) : AutoCloseable {
    private val server = ServerSocket(0)
    private val responses = responseBodies.toList()
    val requests: MutableList<CapturedArrRequest> = Collections.synchronizedList(mutableListOf())
    private val worker = thread(start = true, name = "arr-test-server") {
        responses.forEach { body ->
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                val requestLine = input.readLine().substringBefore(" HTTP/")
                val headers = generateSequence { input.readLine() }.takeWhile(String::isNotEmpty).toList()
                val contentLength = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                val requestBody = CharArray(contentLength).also { chars ->
                    var offset = 0
                    while (offset < chars.size) {
                        val read = input.read(chars, offset, chars.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                }.concatToString()
                requests += CapturedArrRequest(requestLine, requestBody)
                socket.getOutputStream().bufferedWriter().use { output ->
                    output.write("HTTP/1.1 200 OK\r\n")
                    output.write("Content-Type: application/json\r\n")
                    output.write("Content-Length: ${body.toByteArray().size}\r\n")
                    output.write("Connection: close\r\n\r\n")
                    output.write(body)
                }
            }
        }
    }

    fun connection(kind: ArrKind) =
        ArrConnection(kind, kind.displayName, "http://127.0.0.1:${server.localPort}", "test-key")

    fun awaitRequests(expected: Int) {
        worker.join(3_000)
        assertEquals(expected, requests.size)
    }

    override fun close() {
        server.close()
        worker.join(500)
    }
}
