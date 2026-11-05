package com.shinemusic.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.shinemusic.MusicViewModel
import com.shinemusic.ui.components.bottomBarSpace
import com.shinemusic.ui.components.*

private val chips = listOf("Romance", "Feel good", "Relax", "Energize", "Party", "Workout", "Sad", "Focus")

// Quick-tile accents (soft, no red)
private val Pink = Color(0xFFE68AD0)
private val Teal = Color(0xFF4FC3C7)
private val Amber = Color(0xFFF2B35E)
private val Violet = Color(0xFF8E7CF0)

@Composable
fun HomeScreen(
    vm: MusicViewModel,
    openSearch: () -> Unit,
    openLibrary: (Int) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val soon = { Toast.makeText(ctx, "Coming soon", Toast.LENGTH_SHORT).show() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = top + 8.dp, bottom = bottomBarSpace(vm))
    ) {

        // ---- top bar
        item {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).entrance(0),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).glass(CircleShape, tint = cs.primary.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.MusicNote, null, Modifier.size(24.dp), tint = cs.primary) }
                Spacer(Modifier.width(12.dp))
                Text(
                    "ShineMusic",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                        .clickable(onClick = soon),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Person, "Profile", Modifier.size(24.dp), tint = cs.onPrimary) }
            }
        }

        // ---- mood chips
        item {
            LazyRow(
                Modifier.entrance(1),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chips) { c ->
                    GlassPill(onClick = { vm.selectChip(c) }, selected = vm.activeChip == c) {
                        Text(c, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (vm.activeChip != null) {
            item { SectionTitle(vm.activeChip!!) }
            itemsIndexed(vm.chipResults) { i, s ->
                SongRow(s, vm.current?.id == s.id, vm.isPlaying, onClick = { vm.play(vm.chipResults, i) }) {
                    AddToPlaylistButton(vm, s)
                }
            }
            return@LazyColumn
        }

        if (vm.loadingHome && vm.sections.isEmpty()) {
            item { HomeSkeleton() }
        }

        if (!vm.loadingHome && vm.sections.isEmpty()) {
            item {
                Box(Modifier.clickable { vm.loadHome() }) {
                    EmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = "Couldn't load songs",
                        subtitle = "Check your connection and tap here to retry"
                    )
                }
            }
        }

        // ---- hero carousel
        vm.sections.firstOrNull()?.let { (title, songs) ->
            item {
                SectionTitle(title)
                HeroCarousel(
                    songs = songs,
                    currentId = vm.current?.id,
                    isPlaying = vm.isPlaying,
                    onPlay = { vm.play(songs, it) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // ---- rows
        vm.sections.drop(1).let { rest ->
            items(rest) { (title, songs) ->
                SectionTitle(title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                        SongCard(s, playing = vm.current?.id == s.id, animating = vm.isPlaying) { vm.play(songs, i) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
