package com.orgista.openstream.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StremioAddonPresentationTest {
    @Test
    fun `capabilities explain where each addon is used`() {
        assertEquals(
            "Adds rows to Discover · Adds title details · Finds playback streams · Adds captions during playback",
            StremioAddonPresentation.usage(setOf("catalog", "meta", "stream", "subtitles")),
        )
        assertEquals("Adds captions during playback", StremioAddonPresentation.usage(setOf("subtitles")))
        assertEquals("Finds playback streams", StremioAddonPresentation.usage(setOf("stream")))
        assertEquals(
            "Adds recommendations to Discover",
            StremioAddonPresentation.usage(
                StremioAddon(
                    id = "watch-next",
                    manifestId = "community.watch.next",
                    name = "Watch Next",
                    transportUrl = "https://watch-next.example",
                    version = "1.0.0",
                    resources = setOf("stream"),
                ),
            ),
        )
    }
}
