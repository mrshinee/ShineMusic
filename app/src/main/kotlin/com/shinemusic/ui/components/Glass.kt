package com.shinemusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shinemusic.ui.theme.isDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid-glass surface: translucent frosted fill, a diagonal specular sheen
 * and a light-catching gradient rim. [scrim] darkens the base so text stays
 * readable when the glass floats above busy content (mini player, nav bar).
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Transparent,
    wash: Brush? = null,
    strength: Float = 1f,
    scrim: Float = 0f
): Modifier {
    val dark = MaterialTheme.colorScheme.isDark
    val top = (if (dark) 0.17f else 0.78f) * strength
    val bottom = (if (dark) 0.06f else 0.46f) * strength
    val rimHi = (if (dark) 0.55f else 1f).coerceAtMost(1f)
    val rimLo = if (dark) 0.07f else 0.35f
    val scrimColor = if (dark) Color(0xFF05050A) else Color.White
    return this
        .clip(shape)
        .background(scrimColor.copy(alpha = scrim.coerceIn(0f, 1f)))
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = top.coerceIn(0f, 1f)), Color.White.copy(alpha = bottom.coerceIn(0f, 1f)))
            )
        )
        .background(tint)
        .then(if (wash != null) Modifier.background(wash) else Modifier)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = (if (dark) 0.20f else 0.55f) * strength), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f),
                    radius = size.maxDimension * 0.85f
                )
            )
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = rimHi), Color.White.copy(alpha = rimLo), Color.White.copy(alpha = rimHi * 0.4f))
            ),
            shape
        )
}

/** Springy press feedback without a ripple – the "liquid" tap. Put it BEFORE .glass() so the glass scales too. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.93f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    tint: Color = Color.Unspecified,
    active: Boolean = false,
    scrim: Float = 0f,
    contentDescription: String? = null
) {
    val cs = MaterialTheme.colorScheme
    val a by animateFloatAsState(if (active) 1f else 0f, tween(280), label = "active")
    val iconColor by animateColorAsState(
        if (tint.isSpecified) tint else LocalContentColor.current, tween(250), label = "ic"
    )
    Box(
        modifier
            .size(size)
            .bounceClick(onClick = onClick)
            .glass(shape, tint = cs.primary.copy(alpha = 0.38f * a), scrim = scrim),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = iconColor, modifier = Modifier.size(size * 0.48f))
    }
}

/**
 * Slow drifting colour blobs behind the whole app. Gives the glass something
 * to refract, and takes the tint of the currently playing album art.
 */
@Composable
fun AuroraBackground(
    accent: Color,
    modifier: Modifier = Modifier,
    forceDark: Boolean? = null,
    intensity: Float = 1f,
    animate: Boolean = !com.shinemusic.data.AppSettings.reduceAnimations
) {
    val cs = MaterialTheme.colorScheme
    val dark = forceDark ?: cs.isDark
    val base = if (forceDark == true) Color.Black else cs.background
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "aurora")
        val v by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
            label = "t"
        )
        v
    } else 0f
    val second = cs.tertiary
    val third = cs.primary
    val a1 = if (dark) 0.22f else 0.38f
    val a2 = if (dark) 0.09f else 0.26f
    val a3 = if (dark) 0.09f else 0.24f

    Canvas(modifier.fillMaxSize()) {
        val ang = t * 2f * PI.toFloat()
        val w = size.width
        val h = size.height
        drawRect(base)

        fun blob(center: Offset, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = (alpha * intensity).coerceIn(0f, 1f)), Color.Transparent),
                    center = center, radius = radius
                ),
                radius = radius, center = center
            )
        }
        blob(Offset(w * (0.18f + 0.12f * sin(ang)), h * (0.16f + 0.05f * cos(ang))), w * 0.95f, accent, a1)
        blob(Offset(w * (0.92f - 0.12f * cos(ang)), h * (0.48f + 0.07f * sin(ang))), w * 0.80f, second, a2)
        blob(Offset(w * (0.28f + 0.10f * sin(ang + 2f)), h * (0.90f - 0.05f * cos(ang))), w * 0.90f, third, a3)
        // gentle vignette so the bottom bar always has calm ground
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.55f)), startY = h * 0.55f, endY = h))
    }
}
