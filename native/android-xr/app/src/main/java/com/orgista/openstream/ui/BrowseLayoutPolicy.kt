package com.orgista.openstream.ui

/** Pure responsive measurements shared by browse, library, search, and live surfaces. */
data class BrowseLayout(
    val compact: Boolean,
    val featuredHeightDp: Float,
    val heroContentFraction: Float,
    val heroTitleSp: Float,
    val cardWidthDp: Float,
    /** Zero keeps the adaptive tablet grid; compact layouts use an explicit column count. */
    val gridColumns: Int,
    val showFormatInHero: Boolean,
    val horizontalPaddingDp: Float,
    val itemSpacingDp: Float,
)

data class FeaturedProgressStyle(
    val widthDp: Float,
    val heightDp: Float,
    val bottomPaddingDp: Float,
    val trackAlpha: Float,
    val fillAlpha: Float,
)

object BrowseLayoutPolicy {
    private const val COMPACT_BREAKPOINT_DP = 600f

    fun resolve(widthDp: Float, heightDp: Float): BrowseLayout {
        val compact = minOf(widthDp, heightDp) < COMPACT_BREAKPOINT_DP
        return if (compact) {
            val landscapePhone = widthDp > heightDp
            BrowseLayout(
                compact = true,
                featuredHeightDp = if (landscapePhone) {
                    maxOf(180f, heightDp - 72f)
                } else {
                    minOf(widthDp * 1.2f, heightDp * 0.72f)
                },
                heroContentFraction = 1f,
                heroTitleSp = if (landscapePhone) 28f else 34f,
                cardWidthDp = 148f,
                gridColumns = 2,
                showFormatInHero = false,
                horizontalPaddingDp = 20f,
                itemSpacingDp = 12f,
            )
        } else {
            BrowseLayout(
                compact = false,
                featuredHeightDp = ArtworkPresentationPolicy.stageHeightDp(widthDp, heightDp),
                heroContentFraction = 0.6f,
                heroTitleSp = 46f,
                cardWidthDp = 184f,
                gridColumns = 0,
                showFormatInHero = true,
                horizontalPaddingDp = 34f,
                itemSpacingDp = 18f,
            )
        }
    }

    fun featuredProgressStyle(compact: Boolean) = FeaturedProgressStyle(
        widthDp = if (compact) 48f else 60f,
        heightDp = 1.5f,
        bottomPaddingDp = 44f,
        trackAlpha = 0.14f,
        fillAlpha = 0.68f,
    )
}
