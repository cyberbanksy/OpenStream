package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPolicyTest {
    private fun item(rating: String, title: String = "Sample", synopsis: String = "A calm documentary.") = MediaItem(
        id = title.lowercase(),
        kind = MediaKind.MOVIE,
        title = title,
        synopsis = synopsis,
        year = 2026,
        rating = rating,
        runtimeMinutes = 100,
    )

    @Test
    fun `normalizes MPAA and TV certifications`() {
        assertEquals(0, normalizedRatingAge("G"))
        assertEquals(13, normalizedRatingAge("PG-13"))
        assertEquals(13, normalizedRatingAge("pg13"))
        assertEquals(14, normalizedRatingAge("TV-14"))
        assertEquals(17, normalizedRatingAge("R"))
        assertEquals(17, normalizedRatingAge("TV-MA"))
        assertNull(normalizedRatingAge(""))
        assertNull(normalizedRatingAge("Not Rated"))
    }

    @Test
    fun `teens cap allows PG-13 and TV-14 but blocks restricted`() {
        val policy = createKidsPolicy(KidsCap.TEENS, emptyList())
        assertTrue(isItemAllowed(item("PG-13"), policy))
        assertTrue(isItemAllowed(item("TV-14"), policy))
        assertFalse(isItemAllowed(item("R"), policy))
        assertFalse(isItemAllowed(item("TV-MA"), policy))
        assertFalse(isItemAllowed(item("NC-17"), policy))
    }

    @Test
    fun `unrated content is blocked in kids mode and allowed in standard`() {
        val policy = createKidsPolicy(KidsCap.TEENS, emptyList())
        assertFalse(isItemAllowed(item(""), policy))
        assertTrue(isItemAllowed(item(""), ContentPolicy.Standard))
    }

    @Test
    fun `stricter caps block parental guidance titles`() {
        val littleKids = createKidsPolicy(KidsCap.LITTLE_KIDS, emptyList())
        assertTrue(isItemAllowed(item("G"), littleKids))
        assertFalse(isItemAllowed(item("PG"), littleKids))
        val bigKids = createKidsPolicy(KidsCap.BIG_KIDS, emptyList())
        assertTrue(isItemAllowed(item("PG"), bigKids))
        assertFalse(isItemAllowed(item("PG-13"), bigKids))
    }

    @Test
    fun `guardian terms match titles and synopses`() {
        val policy = createKidsPolicy(KidsCap.TEENS, listOf("Redline", "haunted"))
        assertFalse(isItemAllowed(item("PG", title = "Redline"), policy))
        assertFalse(isItemAllowed(item("PG", synopsis = "A haunted lighthouse mystery."), policy))
        assertTrue(isItemAllowed(item("PG", title = "Solar Drift"), policy))
    }

    @Test
    fun `filtering keeps standard mode untouched and fails closed at strictest`() {
        val items = listOf(item("G", "A"), item("R", "B"), item("TV-14", "C"), item("", "D"))
        assertEquals(4, filterCatalog(items, ContentPolicy.Standard).size)
        assertEquals(listOf("A", "C"), filterCatalog(items, createKidsPolicy(KidsCap.TEENS, emptyList())).map { it.title })
        assertEquals(KidsCap.LITTLE_KIDS, strictestKidsPolicy.cap)
        assertFalse(isItemAllowed(item("PG-13"), strictestKidsPolicy))
    }

    @Test
    fun `profile checksum is stable and tamper sensitive`() {
        val sealed = ProfileStore.checksum("""{"mode":"kids"}""")
        assertEquals(sealed, ProfileStore.checksum("""{"mode":"kids"}"""))
        assertFalse(sealed == ProfileStore.checksum("""{"mode":"standard"}"""))
    }
}
