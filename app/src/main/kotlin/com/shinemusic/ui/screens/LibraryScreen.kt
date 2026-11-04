package com.shinemusic.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shinemusic.MusicViewModel
import com.shinemusic.ui.components.EmptyState
import com.shinemusic.ui.components.GlassSegmented
import com.shinemusic.ui.components.SongRow

/** tab 0 = Liked, tab 1 = History */
@Composable
fun LibraryScreen(vm: MusicViewModel, tab: Int, onTab: (Int) -> Unit) {
    val list = if (tab == 0) vm.favorites.toList() else vm.recents.toList()
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Library", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )
        Text(
            if (list.isEmpty()) "Your collection" else "${list.size} songs",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        GlassSegmented(
            listOf("Liked", "History"), tab, onTab,
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (list.isEmpty()) {
            EmptyState(
                icon = if (tab == 0) Icons.Rounded.Favorite else Icons.Rounded.History,
                title = if (tab == 0) "No liked songs yet" else "Nothing played yet",
                subtitle = if (tab == 0) "Tap the heart on any song and it will show up here"
                else "Songs you listen to will appear here"
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 230.dp)) {
                itemsIndexed(list) { i, s ->
                    SongRow(s, vm.current?.id == s.id, vm.isPlaying, onClick = { vm.play(list, i) }) {
                        if (tab == 1 && vm.isFav(s)) {
                            Icon(Icons.Rounded.Favorite, null, tint = cs.tertiary, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
