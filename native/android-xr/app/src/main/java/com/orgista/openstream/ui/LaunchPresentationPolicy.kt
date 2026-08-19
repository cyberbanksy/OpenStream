package com.orgista.openstream.ui

object LaunchPresentationPolicy {
    const val signalMillis = 900
    const val confluenceMillis = 1_300
    const val lockupMillis = 1_100
    const val discoverMillis = 1_700
    const val discoverStartMillis = signalMillis + confluenceMillis + lockupMillis
    const val totalMillis = signalMillis + confluenceMillis + lockupMillis + discoverMillis

    fun durationMillis(animatorsEnabled: Boolean): Long =
        if (animatorsEnabled) totalMillis.toLong() else 220L

    fun progress(elapsedMillis: Int, startMillis: Int, durationMillis: Int): Float =
        ((elapsedMillis - startMillis).toFloat() / durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class OpenStreamBrandBar(val y: Float, val length: Float)

/** Exact 96-grid geometry supplied with the new OpenStream brand package. */
object OpenStreamBrandGeometry {
    const val viewBox = 96f
    const val startX = 22f
    const val strokeWidth = 12.5f
    val bars = listOf(
        OpenStreamBrandBar(y = 31f, length = 40f),
        OpenStreamBrandBar(y = 48.5f, length = 56f),
        OpenStreamBrandBar(y = 66f, length = 40f),
    )
}

/** Keeps the heavy three-bar mark comfortably inside every launcher mask. */
object LauncherIconPresentationPolicy {
    const val adaptiveCanvasDp = 108f
    const val foregroundInsetDp = 36f

    private val contentWidth = OpenStreamBrandGeometry.bars.maxOf { it.length } + OpenStreamBrandGeometry.strokeWidth
    private val contentHeight =
        OpenStreamBrandGeometry.bars.last().y - OpenStreamBrandGeometry.bars.first().y + OpenStreamBrandGeometry.strokeWidth

    fun renderedMarkWidthDp(): Float =
        contentWidth / OpenStreamBrandGeometry.viewBox * (adaptiveCanvasDp - foregroundInsetDp * 2f)

    fun renderedMarkHeightDp(): Float =
        contentHeight / OpenStreamBrandGeometry.viewBox * (adaptiveCanvasDp - foregroundInsetDp * 2f)
}
