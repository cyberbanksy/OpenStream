package com.orgista.openstream.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameParserTest {
    @Test
    fun `parses a clean movie with year`() {
        val result = FilenameParser.parse("The Last Signal (2026).mkv")
        assertTrue(result is ParsedTitle.Movie)
        result as ParsedTitle.Movie
        assertEquals("The Last Signal", result.cleanTitle)
        assertEquals(2026, result.year)
    }

    @Test
    fun `strips scene tags from a movie filename`() {
        val result = FilenameParser.parse("Northstar.2025.1080p.BluRay.x265.DTS-HD.mkv")
        assertTrue(result is ParsedTitle.Movie)
        result as ParsedTitle.Movie
        assertEquals("Northstar", result.cleanTitle)
        assertEquals(2025, result.year)
    }

    @Test
    fun `parses SxxExx episodes`() {
        val result = FilenameParser.parse("Echoes of Mars S01E04 The Relay 1080p WEB-DL.mp4")
        assertTrue(result is ParsedTitle.Episode)
        result as ParsedTitle.Episode
        assertEquals("Echoes of Mars", result.cleanTitle)
        assertEquals(1, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun `parses 1x02 style episodes`() {
        val result = FilenameParser.parse("Frontier.Outpost.2x06.White.Sky.mkv")
        assertTrue(result is ParsedTitle.Episode)
        result as ParsedTitle.Episode
        assertEquals("Frontier Outpost", result.cleanTitle)
        assertEquals(2, result.season)
        assertEquals(6, result.episode)
    }

    @Test
    fun `handles dots and underscores as separators`() {
        val result = FilenameParser.parse("Solar_Drift.2026.2160p.HDR.mp4")
        result as ParsedTitle.Movie
        assertEquals("Solar Drift", result.cleanTitle)
        assertEquals(2026, result.year)
    }

    @Test
    fun `falls back to filename when no year or tags`() {
        val result = FilenameParser.parse("Home Movie.mov")
        result as ParsedTitle.Movie
        assertEquals("Home Movie", result.cleanTitle)
        assertNull(result.year)
    }

    @Test
    fun `does not treat episode numbers as a year`() {
        val result = FilenameParser.parse("Aurora.S02E10.mkv")
        assertTrue(result is ParsedTitle.Episode)
        result as ParsedTitle.Episode
        assertEquals(2, result.season)
        assertEquals(10, result.episode)
    }

    @Test
    fun `numeric-titled movie keeps title and takes the last year as release`() {
        val result = FilenameParser.parse("1992 (2024).mkv")
        result as ParsedTitle.Movie
        assertEquals("1992", result.cleanTitle)
        assertEquals(2024, result.year)
    }

    @Test
    fun `parses a clean movie folder name`() {
        val result = FilenameParser.parseMovieFolder("A Man Called Otto (2022)")
        assertEquals("A Man Called Otto", result.cleanTitle)
        assertEquals(2022, result.year)
    }

    @Test
    fun `movie folder without year keeps the whole name`() {
        val result = FilenameParser.parseMovieFolder("Home Movies")
        assertEquals("Home Movies", result.cleanTitle)
        assertNull(result.year)
    }
}
