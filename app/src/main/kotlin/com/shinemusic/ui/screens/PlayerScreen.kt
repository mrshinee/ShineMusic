package com.shinemusic.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinemusic.MusicViewModel
import com.shinemusic.data.Song
import com.shinemusic.ui.components.AddToPlaylistDialog
import com.shinemusic.ui.components.Artwork
import com.shinemusic.ui.components.AuroraBackground
import com.shinemusic.ui.components.EqualizerBars
import com.shinemusic.ui.components.GlassIconButton
import com.shinemusic.ui.components.PillSeekBar
import com.shinemusic.ui.components.bounceClick
import com.shinemusic.ui.components.glass
import com.shinemusic.ui.components.rememberArtColor
import kotlinx.coroutines.launch

private enum class Mode { ART, LYRICS, QUEUE }

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(vm: MusicViewModel, song: Song, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.ART) }
    var menu by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    // Album-art accent. Falls back to the theme's indigo – never red.
    val accent = rememberArtColor(song.image, cs.primaryContainer)

    // swipe-down-to-dismiss
    val dragY = remember { Animatable(0f) }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val f = (dragY.value / size.height).coerceIn(0f, 1f)
                    translationY = dragY.value
                    alpha = 1f - f * 0.5f
                    val s = 1f - f * 0.08f
                    scaleX = s; scaleY = s
                }
                .background(Color.Black)
                .pointerInputDismiss(dragY, scope, onClose)
        ) {
            // living background: album colour blobs on an opaque dark base
            AuroraBackground(accent = accent, forceDark = true, intensity = 2.6f)
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
            )

            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- header
                val pos = vm.queue.indexOfFirst { it.id == song.id }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(Icons.Rounded.KeyboardArrowDown, onClose, size = 46.dp, contentDescription = "Close")
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "NOW PLAYING", fontSize = 11.sp, letterSpacing = 1.8.sp,
                            fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.65f)
                        )
                        Text(
                            if (pos >= 0 && vm.queue.size > 1) "${pos + 1} of ${vm.queue.size}" else song.artist,
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.size(46.dp))
                }

                // ---- body (artwork / lyrics / queue)
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            (fadeIn(tween(320, delayMillis = 60)) + scaleIn(initialScale = 0.93f, animationSpec = tween(320, delayMillis = 60))) togetherWith
                                (fadeOut(tween(160)) + scaleOut(targetScale = 0.97f, animationSpec = tween(160)))
                        },
                        label = "mode"
                    ) { m ->
                        when (m) {
                            Mode.ART -> ArtPane(song, vm.isPlaying, accent)
                            Mode.LYRICS -> LyricsView(vm)
                            Mode.QUEUE -> QueueView(vm)
                        }
                    }
                }

                // ---- title row + share / like
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = song,
                            transitionSpec = { fadeIn(tween(350, delayMillis = 80)) togetherWith fadeOut(tween(150)) },
                            label = "title"
                        ) { s ->
                            Column {
                                Text(
                                    s.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, modifier = Modifier.basicMarquee(initialDelayMillis = 1800)
                                )
                                Text(
                                    s.artist, color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    GlassIconButton(Icons.Rounded.Share, {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${song.title} – ${song.artist}")
                        }, null))
                    }, size = 52.dp, contentDescription = "Share")
                    Spacer(Modifier.width(10.dp))
                    LikeButton(vm.isFav(song)) { vm.toggleFav(song) }
                }

                // ---- seek bar
                PillSeekBar(
                    progress = if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f,
                    onSeek = { vm.seekTo((it * vm.durationMs).toLong()) },
                    onScrub = { scrub = it },
                    modifier = Modifier.padding(top = 10.dp)
                )
                val shown = scrub?.let { (it * vm.durationMs).toLong() } ?: vm.positionMs
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(shown), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Text(fmt(vm.durationMs), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }

                // ---- transport
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    SkipButton(Icons.Rounded.SkipPrevious, "Previous") { vm.previous() }
                    Spacer(Modifier.width(14.dp))
                    PlayButton(vm.isPlaying) { vm.togglePlay() }
                    Spacer(Modifier.width(14.dp))
                    SkipButton(Icons.Rounded.SkipNext, "Next") { vm.next() }
                }

                // ---- bottom actions
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionBtn(Icons.Rounded.QueueMusic, mode == Mode.QUEUE) { mode = if (mode == Mode.QUEUE) Mode.ART else Mode.QUEUE }
                    Spacer(Modifier.width(8.dp))
                    ActionBtn(Icons.Rounded.Bedtime, vm.sleepMinutes > 0) {
                        vm.setSleepTimer(when (vm.sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 })
                    }
                    Spacer(Modifier.width(8.dp))
                    ActionBtn(Icons.Rounded.Notes, mode == Mode.LYRICS) { mode = if (mode == Mode.LYRICS) Mode.ART else Mode.LYRICS }
                    Spacer(Modifier.width(8.dp))
                    ActionBtn(if (vm.repeatOne) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, vm.repeatOne) { vm.toggleRepeat() }
                    Spacer(Modifier.weight(1f))
                    Box {
                        GlassIconButton(Icons.Rounded.MoreVert, { menu = true }, size = 54.dp, contentDescription = "More")
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (vm.shuffle) "Shuffle: on" else "Shuffle: off") },
                                leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                                onClick = { vm.toggleShuffle(); menu = false })
                            DropdownMenuItem(
                                text = { Text("Add to playlist") },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                                onClick = { menu = false; addToPlaylist = true })
                            DropdownMenuItem(
                                text = { Text(if (vm.sleepMinutes > 0) "Sleep timer: ${vm.sleepMinutes} min" else "Sleep timer: off") },
                                leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                                onClick = {
                                    vm.setSleepTimer(when (vm.sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 })
                                    menu = false
                                })
                        }
                        if (addToPlaylist) AddToPlaylistDialog(vm, song) { addToPlaylist = false }
                    }
                }
            }
        }
    }
}

/** Drag the player down to dismiss it; springs back if released early. */
private fun Modifier.pointerInputDismiss(
    dragY: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    scope: kotlinx.coroutines.CoroutineScope,
    onClose: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        val threshold = 150.dp.toPx()
        detectVerticalDragGestures(
            onDragEnd = {
                scope.launch {
                    if (dragY.value > threshold) onClose()
                    else dragY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f))
                }
            },
            onDragCancel = { scope.launch { dragY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) } }
        ) { change, amount ->
            val next = (dragY.value + amount).coerceAtLeast(0f)
            if (next > 0f || dragY.value > 0f) {
                change.consume()
                scope.launch { dragY.snapTo(next) }
            }
        }
    }
)

/* ------------------------------------------------------------------ */

@Composable
private fun ArtPane(song: Song, playing: Boolean, accent: Color) {
    // artwork "breathes": full size while playing, settles smaller on pause
    val scale by animateFloatAsState(
        if (playing) 1f else 0.88f, spring(dampingRatio = 0.62f, stiffness = 260f), label = "artScale"
    )
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        AnimatedContent(
            targetState = song,
            transitionSpec = {
                (fadeIn(tween(450)) + scaleIn(initialScale = 0.86f, animationSpec = tween(450))) togetherWith
                    (fadeOut(tween(260)) + scaleOut(targetScale = 1.08f, animationSpec = tween(260)))
            },
            label = "art"
        ) { s ->
            val shape = RoundedCornerShape(36.dp)
            Artwork(
                s.image,
                Modifier
                    .size(side)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(30.dp, shape, ambientColor = accent, spotColor = accent),
                shape
            )
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) { first = false; return@LaunchedEffect }
        if (liked) { pop.snapTo(0.55f); pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 380f)) }
    }
    val c by animateColorAsState(if (liked) cs.tertiary else Color.White, tween(250), label = "like")
    Box(
        Modifier.size(52.dp).bounceClick(onClick = onClick)
            .glass(CircleShape, tint = cs.tertiary.copy(alpha = if (liked) 0.22f else 0f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = c,
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        )
    }
}

@Composable
private fun SkipButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.width(82.dp).height(66.dp).bounceClick(scaleDown = 0.9f, onClick = onClick)
            .glass(RoundedCornerShape(50), scrim = 0.1f),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(32.dp)) }
}

/** Squircle when paused, full pill when playing – the shape morphs with a spring. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (playing) 40.dp else 26.dp, spring(dampingRatio = 0.7f, stiffness = 380f), label = "corner"
    )
    Box(
        Modifier.width(104.dp).height(80.dp).bounceClick(scaleDown = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(corner))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE3E4F7)))),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(playing, animationSpec = tween(220), label = "pp") { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pause" else "Play",
                tint = Color(0xFF14142B), modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun ActionBtn(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    GlassIconButton(icon, onClick, size = 52.dp, shape = RoundedCornerShape(18.dp), active = active)
}

/* ------------------------------------------------------------------ */

private fun Modifier.fadingEdges(height: Dp = 40.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h),
            blendMode = BlendMode.DstIn
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - h, endY = size.height),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
private fun LyricsView(vm: MusicViewModel) {
    val lines = vm.lyrics
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No lyrics found", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
        }
        return
    }
    val synced = lines.first().timeMs >= 0
    val active = if (synced) lines.indexOfLast { it.timeMs <= vm.positionMs }.coerceAtLeast(0) else -1
    val state = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) state.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().fadingEdges(),
        contentPadding = PaddingValues(vertical = 48.dp)
    ) {
        itemsIndexed(lines) { i, l ->
            val isActive = i == active
            val a by animateFloatAsState(
                when { !synced -> 0.92f; isActive -> 1f; i < active -> 0.5f; else -> 0.36f },
                tween(380), label = "lineA"
            )
            val sc by animateFloatAsState(
                if (!synced || isActive) 1f else 0.92f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "lineS"
            )
            Text(
                l.text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp,
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        alpha = a; scaleX = sc; scaleY = sc
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = synced) { vm.seekTo(l.timeMs) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun QueueView(vm: MusicViewModel) {
    val idx = vm.queue.indexOfFirst { it.id == vm.current?.id }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 1).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize().fadingEdges(24.dp), state = state, contentPadding = PaddingValues(vertical = 12.dp)) {
        itemsIndexed(vm.queue) { i, s ->
            val playing = vm.current?.id == s.id
            val hl by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "qhl")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.14f * hl))
                    .clickable { vm.playQueueItem(i) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(s.image, Modifier.size(52.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title, color = Color.White,
                        fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        s.artist, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (playing) EqualizerBars(vm.isPlaying, Modifier.padding(end = 8.dp).size(20.dp), Color.White)
            }
        }
    }
}
