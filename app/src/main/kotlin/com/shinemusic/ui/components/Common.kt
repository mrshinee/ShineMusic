package com.shinemusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shinemusic.data.Song
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ */
/*  Basic building blocks                                              */
/* ------------------------------------------------------------------ */

/** Glass chip used for mood chips + tabs. Selection fades a primary tint in. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(300), label = "sel")
    Row(
        modifier = modifier
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .glass(RoundedCornerShape(50), tint = cs.primary.copy(alpha = 0.34f * a))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/** Quick-access tile: tinted icon bubble + label on glass. */
@Composable
fun QuickPill(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(60.dp)
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .glass(RoundedCornerShape(30.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Cover art with a soft cross-fade once the bitmap arrives. */
@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    val ctx = LocalContext.current
    val req = remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(450).build() }
    AsyncImage(
        model = req,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

/** Square album card used in horizontal rows. Shows an animated equalizer badge while it is the current song. */
@Composable
fun SongCard(song: Song, playing: Boolean = false, animating: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).bounceClick(scaleDown = 0.95f, onClick = onClick)) {
        Box {
            Artwork(
                song.image,
                Modifier.size(150.dp).border(
                    if (playing) 2.dp else 1.dp,
                    if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(24.dp)
                ),
                RoundedCornerShape(24.dp)
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = playing,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                enter = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
            ) {
                NowPlayingBadge(animating)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** White circle with the animated equalizer inside – the "now playing" logo. */
@Composable
fun NowPlayingBadge(animating: Boolean, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        EqualizerBars(active = animating, modifier = Modifier.size(size * 0.46f), color = Color.Black)
    }
}

@Composable
fun SongRow(
    song: Song,
    playing: Boolean = false,
    animating: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val hl by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "hl")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cs.primary.copy(alpha = 0.16f * hl))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song.image, Modifier.size(56.dp), RoundedCornerShape(16.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) cs.primary else cs.onSurface
            )
            Text(
                song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
            )
        }
        if (playing) {
            EqualizerBars(active = animating, modifier = Modifier.padding(horizontal = 8.dp).size(22.dp))
        }
        trailing()
    }
}

/* ------------------------------------------------------------------ */
/*  Motion helpers                                                     */
/* ------------------------------------------------------------------ */

/**
 * Animated equalizer / beat visualizer (3 rounded bars).
 * Bars bounce out of phase while [active]; when paused they ease into a calm
 * short-medium-tall pose, so the icon never looks broken.
 */
@Composable
fun EqualizerBars(
    active: Boolean,
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.18f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(740, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.15f, 0.95f, infiniteRepeatable(tween(430, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c")
    val live by animateFloatAsState(if (active) 1f else 0f, tween(350), label = "live")
    val rest = floatArrayOf(0.38f, 0.62f, 1f)

    Canvas(modifier) {
        val anim = if (live > 0f) floatArrayOf(a, b, c) else rest
        val bw = size.width / 5f
        for (i in 0..2) {
            val h = rest[i] + (anim[i] - rest[i]) * live
            val bh = size.height * h
            drawRoundRect(
                color,
                Offset(bw * 2f * i, size.height - bh),
                androidx.compose.ui.geometry.Size(bw, bh),
                CornerRadius(bw / 2f)
            )
        }
    }
}

/** Light sweep used on skeleton placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.onSurface
    this.drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, base.copy(alpha = 0.10f), Color.Transparent),
                start = Offset(x * w, 0f), end = Offset(x * w + w * 0.6f, size.height * 0.4f)
            )
        )
    }
}

/** Fade + rise on first appearance only (remembered across scroll recycling). */
fun Modifier.entrance(index: Int, stepMs: Int = 70): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!shown) { delay(index * stepMs.toLong()); shown = true } }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(520, easing = FastOutSlowInEasing), label = "entrance")
    this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 28.dp.toPx()
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier, shape: Shape = RoundedCornerShape(24.dp)) {
    Box(
        modifier.clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .shimmer()
    )
}

@Composable
fun HomeSkeleton() {
    Column {
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(Modifier.padding(horizontal = 20.dp).width(140.dp).height(22.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.height(14.dp))
        SkeletonBlock(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(380.dp), RoundedCornerShape(36.dp))
        Spacer(Modifier.height(24.dp))
        SkeletonBlock(Modifier.padding(horizontal = 20.dp).width(120.dp).height(22.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { SkeletonBlock(Modifier.size(150.dp)) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).glass(CircleShape, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Glass segmented control with a sliding indicator. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier.fillMaxWidth().height(54.dp).glass(RoundedCornerShape(27.dp), scrim = 0.2f).padding(4.dp)
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selected, spring(dampingRatio = 0.78f, stiffness = 420f), label = "seg")
        Box(
            Modifier.offset(x = x).width(segW).fillMaxHeight()
                .clip(RoundedCornerShape(23.dp))
                .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.55f), cs.primaryContainer.copy(alpha = 0.85f))))
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val c by animateColorAsState(
                    if (i == selected) cs.onSurface else cs.onSurfaceVariant, tween(250), label = "segc"
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(23.dp)).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) { Text(label, style = MaterialTheme.typography.titleSmall, color = c) }
            }
        }
    }
}

fun fadeBrush(color: Color) = Brush.verticalGradient(listOf(Color.Transparent, color))
