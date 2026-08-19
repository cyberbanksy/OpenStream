package com.orgista.openstream.ui

import com.orgista.openstream.data.StremioAddonTestResult

enum class AddonTestIndicatorState {
    READY,
    TESTING,
    PASSED,
    FAILED,
}

object AddonTestIndicatorPolicy {
    fun state(testing: Boolean, result: StremioAddonTestResult?): AddonTestIndicatorState = when {
        testing -> AddonTestIndicatorState.TESTING
        result?.working == true -> AddonTestIndicatorState.PASSED
        result != null -> AddonTestIndicatorState.FAILED
        else -> AddonTestIndicatorState.READY
    }

    fun contentDescription(
        addonName: String,
        testing: Boolean,
        result: StremioAddonTestResult?,
    ): String = when (state(testing, result)) {
        AddonTestIndicatorState.READY -> "Test $addonName"
        AddonTestIndicatorState.TESTING -> "Testing $addonName"
        AddonTestIndicatorState.PASSED -> "$addonName test passed. ${result?.message.orEmpty()}".trim()
        AddonTestIndicatorState.FAILED -> "$addonName test failed. ${result?.message.orEmpty()}".trim()
    }
}
