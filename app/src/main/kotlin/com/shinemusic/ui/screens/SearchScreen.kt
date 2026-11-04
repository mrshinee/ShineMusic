package com.shinemusic.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shinemusic.MusicViewModel
import com.shinemusic.ui.components.EmptyState
import com.shinemusic.ui.components.SongRow
import com.shinemusic.ui.components.glass

@Composable
fun SearchScreen(vm: MusicViewModel) {
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { if (q.isEmpty()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Search", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth()
                .glass(RoundedCornerShape(50), scrim = 0.22f)
        ) {
            TextField(
                value = q,
                onValueChange = { q = it; vm.search(it) },
                singleLine = true,
                placeholder = { Text("Songs, artists, albums…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = cs.primary) },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = ""; vm.search("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }

        if (vm.searching) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).clip(CircleShape),
                trackColor = cs.onSurface.copy(alpha = 0.08f)
            )
        }

        if (vm.searchResults.isEmpty() && !vm.searching) {
            EmptyState(
                icon = Icons.Rounded.MusicNote,
                title = if (q.isBlank()) "Find your next favourite" else "No results",
                subtitle = if (q.isBlank()) "Search for songs, artists or albums" else "Try a different spelling or keyword"
            )
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 230.dp)) {
            itemsIndexed(vm.searchResults) { i, s ->
                SongRow(s, vm.current?.id == s.id, vm.isPlaying, onClick = { vm.play(vm.searchResults, i) })
            }
        }
    }
}
