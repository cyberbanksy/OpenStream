package com.orgista.openstream.domain

import com.orgista.openstream.data.MediaItem
import com.orgista.openstream.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleSearchTest {
    private fun title(
        id: String,
        name: String,
        synopsis: String = "",
        imdbId: String? = null,
    ) = MediaItem(
        id = id,
        kind = MediaKind.MOVIE,
        title = name,
        synopsis = synopsis,
        year = 2020,
        rating = "",
        runtimeMinutes = 0,
        imdbId = imdbId,
    )

    @Test
    fun `exact title ranks before partial and synopsis matches`() {
        val values = listOf(
            title("synopsis", "World War II", synopsis = "The Band of Brothers story"),
            title("partial", "Band of Brothers: The Making Of"),
            title("exact", "Band of Brothers"),
        )

        assertEquals(
            listOf("exact", "partial", "synopsis"),
            TitleSearch.rank(values, "band of brothers").map(MediaItem::id),
        )
    }

    @Test
    fun `punctuation and case do not prevent a title match`() {
        val values = listOf(title("spider", "Spider-Man: No Way Home"))

        assertEquals("spider", TitleSearch.rank(values, "spider man no way home").single().id)
    }

    @Test
    fun `accented and non latin titles remain locally searchable`() {
        val values = listOf(
            title("amelie", "Amélie"),
            title("spirited", "千と千尋の神隠し"),
        )

        assertEquals("amelie", TitleSearch.rank(values, "amelie").single().id)
        assertEquals("spirited", TitleSearch.rank(values, "千と千尋").single().id)
    }

    @Test
    fun `local and remote results merge by imdb identity`() {
        val local = title("local-route", "Band of Brothers", imdbId = "tt0185906")
        val remote = title("tt0185906", "Band of Brothers", synopsis = "Remote metadata", imdbId = "tt0185906")

        val merged = TitleSearch.mergeAndRank(listOf(local), listOf(remote), "Band of Brothers")

        assertEquals(1, merged.size)
        assertEquals("local-route", merged.single().id)
        assertEquals("Remote metadata", merged.single().synopsis)
    }

    @Test
    fun `provider result remains visible when its canonical spelling differs`() {
        val providerResult = title("tt0114369", "Seven", imdbId = "tt0114369")

        val results = TitleSearch.mergeAndRank(emptyList(), listOf(providerResult), "Se7en")

        assertEquals(listOf("tt0114369"), results.map(MediaItem::id))
    }

    @Test
    fun `reusable index returns the same ranking across incremental queries`() {
        val values = listOf(
            title("band", "Band of Brothers"),
            title("bandslam", "Bandslam"),
            title("other", "The Pacific", synopsis = "Companion to Band of Brothers"),
        )
        val index = TitleSearch.index(values)

        assertEquals(listOf("band", "bandslam", "other"), index.rank("band").map(MediaItem::id))
        assertEquals(listOf("band", "other"), index.rank("band of brothers").map(MediaItem::id))
        assertEquals(values, index.rank(""))
    }
}
