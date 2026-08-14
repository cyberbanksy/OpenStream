package com.orgista.openstream.ui

data class SourceSectionLayout(
    val boxedRows: Boolean,
    val showSectionInfo: Boolean,
    val headingSizeSp: Int,
    val minimumRowHeightDp: Int,
)

object SourceSectionLayoutPolicy {
    val current = SourceSectionLayout(
        boxedRows = false,
        showSectionInfo = true,
        headingSizeSp = 18,
        minimumRowHeightDp = 64,
    )
}
