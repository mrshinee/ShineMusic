package com.shinemusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Pill track with a tall vertical thumb (M3-Play look). The track swells while
 * you scrub, progress glides between the 250 ms position ticks, and releasing
 * the thumb never snaps back to the old position.
 */
@Composable
fun PillSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    onScrub: (Float?) -> Unit = {}
) {
    var width by remember { mutableStateOf(1f) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)

    val glide = remember { Animatable(progress.coerceIn(0f, 1f)) }
    LaunchedEffect(progress) {
        if (drag == null) glide.animateTo(progress.coerceIn(0f, 1f), tween(260, easing = LinearEasing))
    }
    val active by animateFloatAsState(
        if (drag != null) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "active"
    )

    Box(
        modifier.fillMaxWidth().height(48.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { seek((it.x / width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        val f = (it.x / width).coerceIn(0f, 1f)
                        drag = f; scrub(f)
                    },
                    onDragEnd = {
                        drag?.let { f ->
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { glide.snapTo(f) }
                            seek(f)
                        }
                        drag = null; scrub(null)
                    },
                    onDragCancel = { drag = null; scrub(null) }
                ) { change, _ ->
                    val f = (change.position.x / width).coerceIn(0f, 1f)
                    drag = f; scrub(f)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackH = lerp(12.dp.toPx(), 18.dp.toPx(), active)
            val gap = lerp(6.dp.toPx(), 9.dp.toPx(), active)
            val thumbW = lerp(4.dp.toPx(), 6.dp.toPx(), active)
            val p = drag ?: glide.value
            val x = p * size.width
            val top = (size.height - trackH) / 2
            val r = CornerRadius(trackH / 2)

            // active (left)
            val aw = (x - gap).coerceAtLeast(0f)
            if (aw > 0) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.72f), color), 0f, aw.coerceAtLeast(1f)),
                    Offset(0f, top), Size(aw, trackH), r
                )
            }
            // inactive (right)
            val ix = x + gap
            if (ix < size.width) {
                drawRoundRect(color.copy(alpha = 0.22f), Offset(ix, top), Size(size.width - ix, trackH), r)
                drawCircle(color, 2.5.dp.toPx(), Offset(size.width - 8.dp.toPx(), size.height / 2))
            }
            // thumb
            val thumbInset = lerp(5.dp.toPx(), 2.dp.toPx(), active)
            drawRoundRect(
                color, Offset(x - thumbW / 2, thumbInset),
                Size(thumbW, size.height - thumbInset * 2), CornerRadius(thumbW / 2)
            )
        }
    }
}
