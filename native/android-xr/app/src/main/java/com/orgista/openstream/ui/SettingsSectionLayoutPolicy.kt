package com.orgista.openstream.ui

data class SettingsSectionLayout(
    val showDivider: Boolean,
    val headingSizeSp: Int,
    val topPaddingDp: Int,
    val bottomPaddingDp: Int,
)

object SettingsSectionLayoutPolicy {
    val current = SettingsSectionLayout(
        showDivider = false,
        headingSizeSp = 18,
        topPaddingDp = 18,
        bottomPaddingDp = 8,
    )
}
