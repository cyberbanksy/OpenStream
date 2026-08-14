package com.orgista.openstream.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val LaunchBackground = Color(0xFF020406)
private val LaunchMark = Color(0xFFF5F8F9)

@Composable
fun OpenStreamLaunchGate(content: @Composable () -> Unit) {
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    var showing by remember { mutableStateOf(true) }
    val timeline = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (animatorsEnabled) {
            timeline.animateTo(
                LaunchPresentationPolicy.totalMillis.toFloat(),
                tween(LaunchPresentationPolicy.totalMillis, easing = LinearEasing),
            )
        } else {
            delay(LaunchPresentationPolicy.durationMillis(animatorsEnabled = false))
        }
        showing = false
    }

    Box(Modifier.fillMaxSize()) {
        // Discover prepares behind the opaque launch stage, then becomes the
        // real destination during the final scene instead of showing a mock.
        content()
        if (showing) {
            OpenStreamLaunchStage(
                elapsedMillis = timeline.value.roundToInt(),
                animatorsEnabled = animatorsEnabled,
            )
        }
    }
}

@Composable
private fun OpenStreamLaunchStage(elapsedMillis: Int, animatorsEnabled: Boolean) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "OpenStream" }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minPx = minOf(widthPx, heightPx)
        val landscape = widthPx > heightPx
        val eased: (Float) -> Float = { FastOutSlowInEasing.transform(it.coerceIn(0f, 1f)) }
        val progress: (Int, Int) -> Float = { start, duration ->
            LaunchPresentationPolicy.progress(elapsedMillis, start, duration)
        }

        val discover = if (animatorsEnabled) {
            eased(progress(LaunchPresentationPolicy.discoverStartMillis + 50, 800))
        } else {
            0f
        }
        val reveal = if (animatorsEnabled) {
            eased(progress(LaunchPresentationPolicy.discoverStartMillis, 720))
        } else {
            0f
        }
        val brandExit = if (animatorsEnabled) {
            1f - eased(progress(3_450, 500))
        } else {
            1f
        }

        Canvas(Modifier.fillMaxSize()) {
            drawRect(LaunchBackground.copy(alpha = 1f - reveal))
            val glowAlpha = (1f - reveal) * 0.22f
            if (glowAlpha > 0.001f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF384046).copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.44f),
                        radius = size.minDimension * 0.42f,
                    ),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.5f, size.height * 0.44f),
                )
            }
        }

        val initialMarkSize = minPx * if (landscape) 0.24f else 0.278f
        val targetMarkSize = with(density) { (if (landscape) 28.dp else 26.dp).toPx() }
        val initialMarkX = (widthPx - initialMarkSize) * 0.5f
        val initialMarkY = heightPx * 0.432f - initialMarkSize * 0.5f
        val lockupMove = if (animatorsEnabled) eased(progress(2_200, 500)) else 1f
        val lockedMarkX = if (landscape) {
            mix(initialMarkX, initialMarkX - widthPx * 0.145f, lockupMove)
        } else {
            initialMarkX
        }
        val lockedMarkY = if (landscape) {
            initialMarkY
        } else {
            mix(initialMarkY, initialMarkY - heightPx * 0.0625f, lockupMove)
        }
        val targetMarkX = with(density) { (if (landscape) 26.dp else 16.dp).toPx() }
        val targetMarkY = with(density) { (if (landscape) 10.dp else 7.dp).toPx() }
        val markSize = mix(initialMarkSize, targetMarkSize, discover)
        val markX = mix(lockedMarkX, targetMarkX, discover)
        val markY = mix(lockedMarkY, targetMarkY, discover)

        LaunchSignalAndSources(
            elapsedMillis = elapsedMillis,
            markX = initialMarkX,
            markY = initialMarkY,
            markSize = initialMarkSize,
            widthPx = widthPx,
            heightPx = heightPx,
            visible = animatorsEnabled,
        )

        OpenStreamAnimatedMark(
            elapsedMillis = elapsedMillis,
            modifier = Modifier
                .offset { IntOffset(markX.roundToInt(), markY.roundToInt()) }
                .size(with(density) { markSize.toDp() })
                .graphicsLayer { alpha = brandExit },
            immediatelyVisible = !animatorsEnabled,
        )

        var wordSize by remember { mutableStateOf(IntSize.Zero) }
        val wordReveal = if (animatorsEnabled) eased(progress(2_250, 620)) else 1f
        val initialWordSp = if (landscape) 31f else 32f
        val targetWordSp = if (landscape) 18f else 17f
        val fontSizeSp = mix(initialWordSp, targetWordSp, discover)
        val wordWidth = wordSize.width.toFloat()
        val wordHeight = wordSize.height.toFloat()
        val lockedWordX = if (landscape) {
            lockedMarkX + initialMarkSize + with(density) { 18.dp.toPx() }
        } else {
            (widthPx - wordWidth) * 0.5f
        }
        val lockedWordY = if (landscape) {
            lockedMarkY + initialMarkSize * 0.5f - wordHeight * 0.5f
        } else {
            lockedMarkY + initialMarkSize + with(density) { 10.dp.toPx() }
        }
        val targetWordX = targetMarkX + targetMarkSize + with(density) { 5.dp.toPx() }
        val targetWordY = targetMarkY + targetMarkSize * 0.5f - wordHeight * 0.5f
        val wordX = mix(lockedWordX, targetWordX, discover)
        val wordY = mix(lockedWordY, targetWordY, discover)

        Text(
            "OpenStream",
            modifier = Modifier
                .offset { IntOffset(wordX.roundToInt(), wordY.roundToInt()) }
                .onSizeChanged { wordSize = it }
                .graphicsLayer {
                    alpha = wordReveal * brandExit
                    translationY = (1f - wordReveal) * with(density) { 14.dp.toPx() }
                },
            color = Color(0xFFF9FBFC),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.28).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun LaunchSignalAndSources(
    elapsedMillis: Int,
    markX: Float,
    markY: Float,
    markSize: Float,
    widthPx: Float,
    heightPx: Float,
    visible: Boolean,
) {
    if (!visible) return
    val signalDraw = FastOutSlowInEasing.transform(LaunchPresentationPolicy.progress(elapsedMillis, 0, 720))
    val signalFade = 1f - LaunchPresentationPolicy.progress(elapsedMillis, 900, 520)
    val streamStarts = listOf(720, 840, 960)

    Canvas(Modifier.fillMaxSize()) {
        if (signalFade > 0f) {
            val halfWidth = widthPx * 0.36f * signalDraw
            val center = Offset(widthPx * 0.5f, heightPx * 0.432f)
            drawLine(
                color = LaunchMark.copy(alpha = signalFade * 0.12f),
                start = Offset(center.x - halfWidth, center.y),
                end = Offset(center.x + halfWidth, center.y),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = LaunchMark.copy(alpha = signalFade * 0.72f),
                start = Offset(center.x - halfWidth, center.y),
                end = Offset(center.x + halfWidth, center.y),
                strokeWidth = 1.5f,
                cap = StrokeCap.Round,
            )
        }

        OpenStreamBrandGeometry.bars.forEachIndexed { index, bar ->
            val streamProgress = FastOutSlowInEasing.transform(
                LaunchPresentationPolicy.progress(elapsedMillis, streamStarts[index], 620),
            )
            val streamAlpha = (sin(PI * streamProgress).toFloat() * 0.82f).coerceAtLeast(0f)
            if (streamProgress > 0f && streamAlpha > 0.001f) {
                val target = Offset(
                    markX + markSize * OpenStreamBrandGeometry.startX / OpenStreamBrandGeometry.viewBox,
                    markY + markSize * bar.y / OpenStreamBrandGeometry.viewBox,
                )
                val start = Offset(-widthPx * 0.12f, target.y + (index - 1) * markSize * 0.18f)
                val head = Offset(
                    mix(start.x, target.x, streamProgress),
                    mix(start.y, target.y, streamProgress),
                )
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, LaunchMark.copy(alpha = streamAlpha)),
                        start = Offset(head.x - widthPx * 0.24f, head.y),
                        end = head,
                    ),
                    start = Offset(head.x - widthPx * 0.24f, head.y),
                    end = head,
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round,
                )
                drawCircle(LaunchMark.copy(alpha = streamAlpha), radius = 3.4f, center = head)
            }
        }
    }

    listOf("LOCAL", "NAS", "ADD-ONS").forEachIndexed { index, label ->
        val streamProgress = FastOutSlowInEasing.transform(
            LaunchPresentationPolicy.progress(elapsedMillis, streamStarts[index], 620),
        )
        val alpha = (sin(PI * streamProgress).toFloat() * 0.72f).coerceAtLeast(0f)
        if (alpha > 0.01f) {
            val targetX = markX + markSize * OpenStreamBrandGeometry.startX / OpenStreamBrandGeometry.viewBox
            val y = markY + markSize * OpenStreamBrandGeometry.bars[index].y / OpenStreamBrandGeometry.viewBox
            Text(
                label,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            mix(-80f, targetX - 72f, streamProgress).roundToInt(),
                            (y - 19f).roundToInt(),
                        )
                    }
                    .graphicsLayer { this.alpha = alpha },
                color = LaunchMark,
                fontSize = 6.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

@Composable
private fun OpenStreamAnimatedMark(
    elapsedMillis: Int,
    modifier: Modifier,
    immediatelyVisible: Boolean,
) {
    Canvas(modifier) {
        val scale = size.minDimension / OpenStreamBrandGeometry.viewBox
        val stroke = OpenStreamBrandGeometry.strokeWidth * scale
        OpenStreamBrandGeometry.bars.forEachIndexed { index, bar ->
            val barProgress = if (immediatelyVisible) {
                1f
            } else {
                FastOutSlowInEasing.transform(
                    LaunchPresentationPolicy.progress(elapsedMillis, 1_150 + index * 140, 720),
                )
            }
            if (barProgress <= 0f) return@forEachIndexed
            val start = Offset(OpenStreamBrandGeometry.startX * scale, bar.y * scale)
            val end = Offset((OpenStreamBrandGeometry.startX + bar.length * barProgress) * scale, bar.y * scale)
            drawLine(
                color = LaunchMark.copy(alpha = 0.14f),
                start = start,
                end = end,
                strokeWidth = stroke * 1.75f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = LaunchMark,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (!immediatelyVisible && barProgress < 0.985f) {
                drawCircle(Color.White, radius = stroke * 0.37f, center = end)
            }
        }
    }
}

private fun mix(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress.coerceIn(0f, 1f)
