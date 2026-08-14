package com.orgista.openstream.ui

import com.orgista.openstream.data.StremioAddonTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonTestIndicatorPolicyTest {
    @Test
    fun `indicator moves from test to progress and a binary result`() {
        assertEquals(AddonTestIndicatorState.READY, AddonTestIndicatorPolicy.state(false, null))
        assertEquals(AddonTestIndicatorState.TESTING, AddonTestIndicatorPolicy.state(true, null))
        assertEquals(
            AddonTestIndicatorState.PASSED,
            AddonTestIndicatorPolicy.state(false, StremioAddonTestResult(true, "Working.")),
        )
        assertEquals(
            AddonTestIndicatorState.FAILED,
            AddonTestIndicatorPolicy.state(false, StremioAddonTestResult(false, "Unavailable.")),
        )
    }

    @Test
    fun `diagnostic words remain available to accessibility without visible row copy`() {
        val result = StremioAddonTestResult(false, "Add-on could not be reached.")
        val description = AddonTestIndicatorPolicy.contentDescription("Cinemeta", false, result)

        assertTrue(description.startsWith("Cinemeta test failed."))
        assertTrue(description.contains(result.message))
    }
}
