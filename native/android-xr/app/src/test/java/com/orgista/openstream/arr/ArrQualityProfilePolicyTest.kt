package com.orgista.openstream.arr

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrQualityProfilePolicyTest {
    @Test
    fun `highest robust profile wins over broad Any and HD profiles`() {
        val profiles = JSONArray(
            """
            [
              {
                "id": 1,
                "name": "Any",
                "cutoff": 10,
                "items": [
                  {"quality":{"id":10,"name":"WEBDL-1080p","source":"web","resolution":1080},"allowed":true},
                  {"quality":{"id":30,"name":"Remux-2160p","source":"blurayRaw","resolution":2160},"allowed":true}
                ]
              },
              {
                "id": 2,
                "name": "HD-1080p",
                "cutoff": 20,
                "items": [
                  {"quality":{"id":20,"name":"Bluray-1080p","source":"bluray","resolution":1080},"allowed":true}
                ]
              },
              {
                "id": 3,
                "name": "Remux-2160p",
                "cutoff": 30,
                "items": [
                  {"quality":{"id":30,"name":"Remux-2160p","source":"blurayRaw","resolution":2160},"allowed":true}
                ]
              }
            ]
            """.trimIndent(),
        )

        assertEquals(3, ArrQualityProfilePolicy.selectHighestProfileId(profiles))
    }

    @Test
    fun `profile name is a reliable fallback when quality items are omitted`() {
        val profiles = JSONArray(
            """[
                {"id":4,"name":"HD-1080p"},
                {"id":5,"name":"Ultra-HD 2160p"},
                {"id":6,"name":"SD"}
            ]""",
        )

        assertEquals(5, ArrQualityProfilePolicy.selectHighestProfileId(profiles))
    }
}
