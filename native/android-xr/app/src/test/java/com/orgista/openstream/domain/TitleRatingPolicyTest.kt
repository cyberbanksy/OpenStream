package com.orgista.openstream.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleRatingPolicyTest {
    @Test
    fun `thumb selections are mutually exclusive and can be cleared`() {
        assertEquals(TitleRating.UP, TitleRatingPolicy.toggle(TitleRating.NONE, TitleRating.UP))
        assertEquals(TitleRating.DOWN, TitleRatingPolicy.toggle(TitleRating.UP, TitleRating.DOWN))
        assertEquals(TitleRating.NONE, TitleRatingPolicy.toggle(TitleRating.DOWN, TitleRating.DOWN))
    }
}
