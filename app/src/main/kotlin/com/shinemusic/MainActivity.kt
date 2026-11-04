package com.shinemusic

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shinemusic.ui.components.Artwork
import com.shinemusic.ui.components.AuroraBackground
import com.shinemusic.ui.components.GlassIconButton
import com.shinemusic.ui.components.NowPlayingBadge
import com.shinemusic.ui.components.bounceClick
import com.shinemusic.ui.components.glass
import com.shinemusic.ui.components.rememberArtColor
import com.shinemusic.ui.screens.HomeScreen
import com.shinemusic.ui.screens.LibraryScreen
import com.shinemusic.ui.screens.PlayerScreen
import com.shinemusic.ui.screens.SearchScreen
import com.shinemusic.ui.theme.ShineTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Explore),
    SEARCH("Search", Icons.Rounded.Search),
    LIBRARY("Library", Icons.Rounded.Favorite),
    RECENT("Recent", Icons.Rounded.GridView)
}

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { ShineTheme { App(vm) } }
    }
}

@Composable
private fun App(vm: MusicViewModel) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(Tab.HOME) }
    var libTab by remember { mutableStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    val song = vm.current
    val homeState = rememberLazyListState()   // keeps Home scroll position across tab switches
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // The whole app picks up a tint from the song that is playing.
    val accent = rememberArtColor(song?.image, cs.primaryContainer)

    BackHandler(showPlayer) { showPlayer = false }
    BackHandler(!showPlayer && tab != Tab.HOME) { tab = Tab.HOME }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(accent = accent)

        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(300, delayMillis = 70)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300, delayMillis = 70))) togetherWith
                    fadeOut(tween(140))
            },
            label = "tab"
        ) { t ->
            when (t) {
                Tab.HOME -> HomeScreen(vm, { tab = Tab.SEARCH }, { libTab = it; tab = Tab.LIBRARY }, homeState)
                Tab.SEARCH -> SearchScreen(vm)
                Tab.LIBRARY -> LibraryScreen(vm, libTab) { libTab = it }
                Tab.RECENT -> LibraryScreen(vm, 1) { libTab = it; tab = Tab.LIBRARY }
            }
        }

        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp)
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
        )

        // ---- mini player + floating nav
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)) {
            AnimatedVisibility(
                visible = song != null,
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                MiniPlayer(vm, accent, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { showPlayer = true }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatingNav(tab, Modifier.weight(1f)) {
                    if (it != tab) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    tab = it
                }
                Spacer(Modifier.width(12.dp))
                GlassIconButton(
                    Icons.Rounded.MoreHoriz,
                    { Toast.makeText(ctx, "Coming soon", Toast.LENGTH_SHORT).show() },
                    size = 68.dp, shape = RoundedCornerShape(26.dp), scrim = 0.9f, contentDescription = "More"
                )
            }
        }

        AnimatedVisibility(
            visible = showPlayer && song != null,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 330f)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(240))
        ) {
            if (song != null) PlayerScreen(vm, song) { showPlayer = false }
        }
    }
}

@Composable
private fun FloatingNav(selected: Tab, modifier: Modifier, onSelect: (Tab) -> Unit) {
    Row(
        modifier.height(68.dp)
            .glass(RoundedCornerShape(34.dp), scrim = 0.9f)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { t -> NavItem(t, t == selected) { onSelect(t) } }
    }
}

@Composable
private fun RowScope.NavItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // width and pill fade are spring-driven, so the selection "flows" between tabs
    val w by animateFloatAsState(if (selected) 2.1f else 1f, spring(dampingRatio = 0.8f, stiffness = 380f), label = "w")
    val bg by animateFloatAsState(if (selected) 1f else 0f, tween(320), label = "bg")
    val ic by animateColorAsState(if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, tween(250), label = "ic")
    Row(
        Modifier.weight(w).fillMaxHeight()
            .bounceClick(scaleDown = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(cs.primaryContainer.copy(alpha = 0.92f * bg)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(t.icon, t.label, Modifier.size(24.dp), tint = ic)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(220, delayMillis = 80)) + expandHorizontally(spring(stiffness = 420f)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(spring(stiffness = 420f))
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(
                    t.label, style = MaterialTheme.typography.labelLarge, color = ic,
                    maxLines = 1, softWrap = false
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: MusicViewModel, accent: Color, modifier: Modifier, onOpen: () -> Unit) {
    val s = vm.current ?: return
    val cs = MaterialTheme.colorScheme

    // vinyl-style slow spin while playing, freezes in place on pause
    val rot = remember { Animatable(0f) }
    LaunchedEffect(vm.isPlaying) {
        if (vm.isPlaying) {
            while (true) rot.animateTo(rot.value + 360f, tween(14000, easing = LinearEasing))
        }
    }
    val target = if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f
    val progress by animateFloatAsState(target.coerceIn(0f, 1f), tween(260, easing = LinearEasing), label = "mp")

    // near-opaque dark glass tinted with the album colour: always legible, still "liquid"
    Column(
        modifier.fillMaxWidth()
            .bounceClick(scaleDown = 0.97f, onClick = onOpen)
            .glass(
                RoundedCornerShape(30.dp),
                tint = accent.copy(alpha = 0.26f),
                wash = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent)),
                scrim = 0.88f
            )
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
                Artwork(s.image, Modifier.size(50.dp).graphicsLayer { rotationZ = rot.value }, CircleShape)
                // centre hole while paused; animated equalizer badge while playing
                if (vm.isPlaying) NowPlayingBadge(animating = true, size = 26.dp)
                else Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Black))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.title, style = MaterialTheme.typography.titleSmall, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    s.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
            IconButton(onClick = { vm.previous() }, Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.85f))
            }
            Box(
                Modifier.padding(horizontal = 4.dp).size(46.dp).bounceClick(scaleDown = 0.88f) { vm.togglePlay() }
                    .clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (vm.isPlaying) "Pause" else "Play", Modifier.size(28.dp), tint = Color.Black
                )
            }
            IconButton(onClick = { vm.next() }, Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(26.dp), tint = Color.White.copy(alpha = 0.85f))
            }
        }
        // progress runs along the bottom edge of the card
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.10f))) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(accent, Color.White)))
            )
        }
    }
}
