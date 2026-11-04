package com.shinemusic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.shinemusic.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val MaxCardW = 290.dp
private const val CardRatio = 420f / 290f   // height / width

/**
 * Centered cover-flow style carousel built on HorizontalPager:
 *  - the focused card is full size, neighbours shrink, fade, dip and tilt
 *  - the artwork has a parallax shift against the card
 *  - the info plate fades in only on the focused card
 *  - a light haptic tick on every page change, fast flings glide several pages
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    songs: List<Song>,
    currentId: String?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit
) {
    val state = rememberPagerState(pageCount = { songs.size })
    val haptics = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // gentle auto-advance; pauses while the user is touching, resumes 4.5 s after release
    val dragged by state.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged, songs.size) {
        if (dragged || songs.size < 2) return@LaunchedEffect
        while (true) {
            delay(4500)
            if (!state.isScrollInProgress) state.animateScrollToPage((state.currentPage + 1) % songs.size)
        }
    }

    // colour of the focused cover glows behind the carousel
    val glow = rememberArtColor(songs.getOrNull(state.currentPage)?.image, cs.primaryContainer)

    val fling = PagerDefaults.flingBehavior(
        state = state,
        pagerSnapDistance = PagerSnapDistance.atMost(4),
        snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    )

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // card scales with the screen: 78% of the width, never wider than 290dp (phones, foldables, tablets)
            val cardW = minOf(MaxCardW, maxWidth * 0.78f)
            val cardH = cardW * CardRatio
            Box(
                Modifier.align(Alignment.Center).width(cardW * 1.5f).height(cardH)
                    .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.50f), Color.Transparent)))
            )
            val side = ((maxWidth - cardW) / 2).coerceAtLeast(16.dp)
            HorizontalPager(
                state = state,
                contentPadding = PaddingValues(horizontal = side),
                pageSize = PageSize.Fixed(cardW),
                pageSpacing = 14.dp,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val song = songs[page]
                val offset = (state.currentPage - page) + state.currentPageOffsetFraction
                val f = offset.absoluteValue.coerceAtMost(1f)
                val shape = RoundedCornerShape(38.dp)

                Box(
                    Modifier
                        .width(cardW).height(cardH)
                        .graphicsLayer {
                            val s = lerp(1f, 0.78f, f)
                            scaleX = s; scaleY = s
                            alpha = lerp(1f, 0.42f, f)
                            rotationY = offset.coerceIn(-1f, 1f) * 22f
                            cameraDistance = 14f * density
                            translationY = f * 26.dp.toPx()
                        }
                        .clip(shape)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                        .bounceClick(scaleDown = 0.97f) { onPlay(page) }
                ) {
                    // parallax artwork
                    Artwork(
                        song.image,
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = 1.22f; scaleY = 1.22f
                            translationX = offset.coerceIn(-1f, 1f) * size.width * 0.10f
                        },
                        RoundedCornerShape(0.dp)
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xB0000000)), startY = cardH.value * 1.9f)
                        )
                    )

                    // glass info plate, only visible on the focused card
                    val playingThis = currentId == song.id
                    Row(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .graphicsLayer {
                                alpha = (1f - f * 2f).coerceIn(0f, 1f)
                                translationY = f * 48.dp.toPx()
                            }
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(28.dp), scrim = 0.38f)
                            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playingThis) EqualizerBars(active = isPlaying, modifier = Modifier.size(22.dp), color = Color.Black)
                            else Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        // sliding position indicator
        val trackW = 104.dp
        val thumbW = 30.dp
        Box(
            Modifier.padding(top = 16.dp).width(trackW).height(5.dp).clip(CircleShape)
                .background(cs.onSurface.copy(alpha = 0.14f))
        ) {
            Box(
                Modifier
                    .offset {
                        val frac = if (songs.size > 1)
                            ((state.currentPage + state.currentPageOffsetFraction) / (songs.size - 1)).coerceIn(0f, 1f)
                        else 0f
                        IntOffset(((trackW - thumbW).toPx() * frac).roundToInt(), 0)
                    }
                    .width(thumbW).fillMaxHeight().clip(CircleShape)
                    .background(cs.primary)
            )
        }
    }
}
