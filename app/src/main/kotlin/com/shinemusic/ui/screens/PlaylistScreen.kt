package com.shinemusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shinemusic.MusicViewModel
import com.shinemusic.ui.components.bottomBarSpace
import com.shinemusic.ui.components.Artwork
import com.shinemusic.ui.components.EmptyState
import com.shinemusic.ui.components.GlassIconButton
import com.shinemusic.ui.components.PlaylistNameDialog
import com.shinemusic.ui.components.SongRow
import com.shinemusic.ui.components.glass

private const val LIKED = "__liked__"

/** Playlist tab: list of playlists (Liked songs first) -> tap one to open its songs. */
@Composable
fun PlaylistScreen(vm: MusicViewModel, backEnabled: Boolean = true) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val id = openId
    if (id == null) PlaylistList(vm) { openId = it }
    else PlaylistDetail(vm, id, backEnabled) { openId = null }
}

@Composable
private fun PlaylistList(vm: MusicViewModel, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Playlists", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
        )
        Text(
            "${vm.playlists.size + 1} playlists", style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = bottomBarSpace(vm))) {
            item {
                PlaylistRow("Create playlist", "Make a new playlist", null, Icons.Rounded.Add) { creating = true }
            }
            item {
                PlaylistRow("Liked songs", "${vm.favorites.size} songs", null, Icons.Rounded.Favorite) { onOpen(LIKED) }
            }
            items(vm.playlists.toList(), key = { it.id }) { p ->
                PlaylistRow(p.name, "${p.songs.size} songs", p.songs.firstOrNull()?.image, Icons.Rounded.PlaylistPlay) {
                    onOpen(p.id)
                }
            }
            if (vm.playlists.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.PlaylistAdd, title = "No playlists yet",
                        subtitle = "Tap Create playlist, then add songs with the playlist button in Search or the player"
                    )
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "New playlist", confirmLabel = "Create",
            onConfirm = { n -> vm.createPlaylist(n)?.let { onOpen(it.id) }; creating = false },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, coverUrl: String?, icon: ImageVector, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverUrl != null) Artwork(coverUrl, Modifier.size(56.dp), RoundedCornerShape(16.dp))
        else Box(
            Modifier.size(56.dp).glass(RoundedCornerShape(16.dp), tint = cs.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = cs.primary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaylistDetail(vm: MusicViewModel, id: String, backEnabled: Boolean, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isLiked = id == LIKED
    val pl = vm.playlists.firstOrNull { it.id == id }

    BackHandler(enabled = backEnabled, onBack = onBack)

    if (!isLiked && pl == null) {          // deleted -> go back to the list
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val songs = if (isLiked) vm.favorites.toList() else pl!!.songs
    val name = if (isLiked) "Liked songs" else pl!!.name
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.ArrowBack, onBack, contentDescription = "Back")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${songs.size} songs", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            if (!isLiked) {
                Box {
                    GlassIconButton(Icons.Rounded.MoreVert, { menu = true }, contentDescription = "More")
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; renaming = true })
                        DropdownMenuItem(
                            text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; deleting = true })
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.play(songs, 0) }, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { vm.play(songs.shuffled(), 0) }, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle")
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = bottomBarSpace(vm))) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, vm.current?.id == s.id, vm.isPlaying, onClick = { vm.play(songs, i) }) {
                        if (isLiked) {
                            IconButton(onClick = { vm.toggleFav(s) }) {
                                Icon(Icons.Rounded.Favorite, "Unlike", tint = cs.tertiary)
                            }
                        } else {
                            IconButton(onClick = { vm.removeFromPlaylist(id, s.id) }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, "Remove from playlist", tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            EmptyState(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.PlaylistAdd,
                title = if (isLiked) "No liked songs yet" else "This playlist is empty",
                subtitle = if (isLiked) "Tap the heart on any song and it will show up here"
                else "Add songs with the playlist button in Search or in the player menu"
            )
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = "Rename playlist", confirmLabel = "Save", initial = name,
            onConfirm = { vm.renamePlaylist(id, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"$name\" will be deleted. The songs themselves are not affected.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deletePlaylist(id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}
