package com.shinemusic.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shinemusic.MusicViewModel
import com.shinemusic.data.Song

/** Name dialog used for both "New playlist" and "Rename". */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** "Add to playlist" picker: choose an existing playlist or create a new one with this song in it. */
@Composable
fun AddToPlaylistDialog(vm: MusicViewModel, song: Song, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        PlaylistNameDialog(
            title = "New playlist", confirmLabel = "Create",
            onConfirm = { n ->
                vm.createPlaylist(n, song)
                Toast.makeText(ctx, "Added to $n", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { creating = false }
        )
    } else {
        val cs = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add to playlist") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = cs.primary)
                            Spacer(Modifier.width(14.dp))
                            Text("New playlist", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        }
                    }
                    items(vm.playlists.toList(), key = { it.id }) { p ->
                        val has = p.songs.any { it.id == song.id }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val added = vm.addToPlaylist(p.id, song)
                                Toast.makeText(
                                    ctx, if (added) "Added to ${p.name}" else "Already in ${p.name}", Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, null, tint = cs.onSurfaceVariant)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    "${p.songs.size} songs", style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            if (has) Icon(Icons.Rounded.Check, "Already added", tint = cs.primary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}

/** Small "+ playlist" icon for song rows. */
@Composable
fun AddToPlaylistButton(vm: MusicViewModel, song: Song) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(Icons.Rounded.PlaylistAdd, "Add to playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (show) AddToPlaylistDialog(vm, song) { show = false }
}
