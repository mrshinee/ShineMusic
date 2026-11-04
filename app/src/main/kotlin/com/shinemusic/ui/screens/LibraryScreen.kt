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
import com.shinemusic.ui.components.SongRow

/** Recently played songs. */
@Composable
fun HistoryScreen(vm: MusicViewModel) {
    val list = vm.recents.toList()
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "History", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )
        Text(
            if (list.isEmpty()) "Recently played" else "${list.size} songs",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        )
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History, title = "Nothing played yet",
                subtitle = "Songs you listen to will appear here"
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 230.dp)) {
                itemsIndexed(list) { i, s ->
                    SongRow(s, vm.current?.id == s.id, vm.isPlaying, onClick = { vm.play(list, i) }) {
                        if (vm.isFav(s)) {
                            Icon(Icons.Rounded.Favorite, null, tint = cs.tertiary, modifier = Modifier.padding(end = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
