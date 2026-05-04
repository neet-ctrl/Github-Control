package com.githubcontrol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent

/**
 * Interactive 3×3 pattern-lock composable drawn on a Canvas.
 *
 * Usage:
 *   var reset by remember { mutableIntStateOf(0) }
 *   PatternLock(
 *       resetSignal = reset,
 *       onPatternComplete = { indices -> /* indices is List<Int> 0..8 */ }
 *   )
 *
 * A pattern is accepted when ≥ 4 dots are connected. Fewer dots show an error flash.
 */
@Composable
fun PatternLock(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resetSignal: Int = 0,
    primaryColor: Color = Color(0xFF388BFD),
    errorColor: Color = Color(0xFFE53935),
    dotColor: Color = Color.White,
    onPatternChange: (List<Int>) -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit
) {
    val centers = remember { mutableStateOf(List(9) { Offset.Zero }) }
    var pattern  by remember(resetSignal) { mutableStateOf(listOf<Int>()) }
    var touchPos by remember(resetSignal) { mutableStateOf<Offset?>(null) }
    var isError  by remember(resetSignal) { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp)
            .pointerInput(enabled, resetSignal) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pattern = emptyList()
                    isError = false
                    touchPos = down.position
                    val first = nearestDot(centers.value, down.position)
                    if (first >= 0) { pattern = listOf(first); onPatternChange(pattern) }

                    while (true) {
                        val ev = awaitPointerEvent()
                        val p  = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!p.pressed) break
                        touchPos = p.position
                        val hit = nearestDot(centers.value, p.position)
                        if (hit >= 0 && hit !in pattern) {
                            pattern = pattern + hit
                            onPatternChange(pattern)
                        }
                    }
                    touchPos = null
                    when {
                        pattern.size >= 4 -> onPatternComplete(pattern)
                        pattern.isNotEmpty() -> isError = true
                    }
                }
            }
    ) {
        val cellW = size.width  / 3f
        val cellH = size.height / 3f
        val r     = cellW.coerceAtMost(cellH) * 0.12f
        val ringR = r * 1.9f

        val pts = (0 until 9).map { i ->
            Offset(cellW * (i % 3) + cellW / 2f, cellH * (i / 3) + cellH / 2f)
        }
        centers.value = pts

        val active = if (isError) errorColor else primaryColor

        // Lines between selected dots
        for (i in 1 until pattern.size) {
            drawLine(
                color = active.copy(alpha = 0.75f),
                start = pts[pattern[i - 1]],
                end   = pts[pattern[i]],
                strokeWidth = 8f,
                cap   = StrokeCap.Round
            )
        }
        // Live line to finger
        if (pattern.isNotEmpty() && touchPos != null) {
            drawLine(
                color = active.copy(alpha = 0.4f),
                start = pts[pattern.last()],
                end   = touchPos!!,
                strokeWidth = 5f,
                cap   = StrokeCap.Round
            )
        }

        // Dots
        pts.forEachIndexed { i, c ->
            if (i in pattern) {
                drawCircle(color = active.copy(alpha = 0.15f), radius = ringR, center = c)
                drawCircle(color = active, radius = ringR, center = c, style = Stroke(4f))
                drawCircle(color = active, radius = r * 0.55f, center = c)
            } else {
                drawCircle(color = dotColor.copy(alpha = 0.20f), radius = r * 1.0f, center = c)
                drawCircle(color = dotColor.copy(alpha = 0.55f), radius = r, center = c, style = Stroke(2.5f))
            }
        }
    }
}

private fun nearestDot(centers: List<Offset>, touch: Offset, threshold: Float = 66f): Int {
    centers.forEachIndexed { i, c ->
        val dx = c.x - touch.x; val dy = c.y - touch.y
        if (dx * dx + dy * dy < threshold * threshold) return i
    }
    return -1
}
