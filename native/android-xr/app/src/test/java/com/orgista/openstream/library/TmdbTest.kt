package com.orgista.openstream.library

import com.orgista.openstream.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTest {
    private val tmdb = Tmdb("test-key")

    @Test
    fun `find response selects movie by exact imdb id result type`() {
        val result = tmdb.parseFindResult(
            """{"movie_results":[{"id":99,"title":"Exact Film","overview":"Plot","release_date":"2025-05-01","poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":8.1}],"tv_results":[]}""",
            MediaKind.MOVIE,
        )

        assertEquals(99, result?.tmdbId)
        assertEquals("Exact Film", result?.title)
        assertEquals(2025, result?.year)
    }

    @Test
    fun `find response selects tv result for a series`() {
        val result = tmdb.parseFindResult(
            """{"movie_results":[],"tv_results":[{"id":77,"name":"Exact Series","overview":"Plot","first_air_date":"2024-01-01","poster_path":"/p.jpg","backdrop_path":"/b.jpg","vote_average":7.5}]}""",
            MediaKind.SERIES,
        )

        assertEquals(77, result?.tmdbId)
        assertEquals("Exact Series", result?.title)
    }

    @Test
    fun `trailer selection prefers an official english youtube trailer`() {
        val key = tmdb.parseTrailerKey(
            """{"results":[
                {"site":"YouTube","type":"Teaser","official":true,"iso_639_1":"en","key":"teaser_key"},
                {"site":"YouTube","type":"Trailer","official":false,"iso_639_1":"en","key":"unofficial_key"},
                {"site":"YouTube","type":"Trailer","official":true,"iso_639_1":"fr","key":"official_fr"},
                {"site":"YouTube","type":"Trailer","official":true,"iso_639_1":"en","key":"official_en"}
            ]}""",
        )

        assertEquals("official_en", key)
    }

    @Test
    fun `trailer selection falls back to a youtube teaser and rejects unsafe ids`() {
        assertEquals(
            "safe-teaser_1",
            tmdb.parseTrailerKey(
                """{"results":[
                    {"site":"Vimeo","type":"Trailer","official":true,"iso_639_1":"en","key":"vimeo"},
                    {"site":"YouTube","type":"Trailer","official":true,"iso_639_1":"en","key":"bad/key"},
                    {"site":"YouTube","type":"Teaser","official":false,"iso_639_1":"en","key":"safe-teaser_1"}
                ]}""",
            ),
        )
        assertNull(tmdb.parseTrailerKey("""{"results":[]}"""))
    }
}
