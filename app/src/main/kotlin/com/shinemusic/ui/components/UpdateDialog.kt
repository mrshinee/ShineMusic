package com.shinemusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shinemusic.MusicViewModel

private sealed interface NoteLine {
    class Heading(val text: String, val level: Int) : NoteLine
    class Bullet(val text: String) : NoteLine
    class Para(val text: String) : NoteLine
}

/** Minimal markdown -> lines: # headings, - bullets, **bold** markers stripped. */
private fun parseNotes(md: String): List<NoteLine> =
    md.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
        val clean = raw.replace("**", "").replace("__", "").replace("`", "")
        when {
            clean.startsWith("#") -> NoteLine.Heading(clean.trimStart('#').trim(), clean.takeWhile { it == '#' }.length)
            clean.startsWith("- ") || clean.startsWith("* ") -> NoteLine.Bullet(clean.drop(2).trim())
            else -> NoteLine.Para(clean)
        }
    }

/** "Update Available" card: version chip, expandable What's new, Download / Install and Later. */
@Composable
fun UpdateDialog(vm: MusicViewModel) {
    val info = vm.update
    if (!vm.showUpdate || info == null) return

    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(true) }
    val notes = remember(info.notes) { parseNotes(info.notes) }
    val downloading = vm.updateProgress >= 0f
    val ready = vm.updateFile != null

    Dialog(
        onDismissRequest = { vm.dismissUpdate() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(cs.primary.copy(alpha = 0.28f).blendOver(Color(0xFF1D1F28)), Color(0xFF1D1F28)),
                        endY = 520f
                    )
                )
                .padding(24.dp)
        ) {
            // ---- header
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(34.dp), tint = cs.primary) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Update Available", fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6E8F2))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.version,
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.primary
                    )
                }
                Icon(
                    Icons.Rounded.Close, "Close",
                    Modifier.size(32.dp).clip(CircleShape).clickable { vm.dismissUpdate() }.padding(4.dp),
                    tint = Color(0xFF9AA0B5)
                )
            }

            // ---- what's new
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("What's new", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB4B8CC))
                    Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = Color(0xFFB4B8CC))
                }
                AnimatedVisibility(expanded) {
                    Column(
                        Modifier.padding(top = 12.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        notes.forEach { line ->
                            when (line) {
                                is NoteLine.Heading -> Text(
                                    line.text,
                                    fontSize = if (line.level <= 1) 22.sp else if (line.level == 2) 18.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (line.level >= 3) Color(0xFFB4B8CC) else Color(0xFFE6E8F2)
                                )
                                is NoteLine.Bullet -> Row {
                                    Text("•", Modifier.width(22.dp), fontSize = 15.sp, color = cs.primary)
                                    Text(line.text, fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFFC9CCDB))
                                }
                                is NoteLine.Para -> Text(line.text, fontSize = 15.sp, lineHeight = 22.sp, color = Color(0xFFC9CCDB))
                            }
                        }
                    }
                }
            }

            vm.updateError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 13.sp, color = Color(0xFFFFB4AB))
            }

            Spacer(Modifier.height(24.dp))

            // ---- Download / progress / Install button
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(CircleShape)
                    .background(if (downloading) cs.primary.copy(alpha = 0.30f) else cs.primary)
                    .clickable(enabled = !downloading) { if (ready) vm.installUpdate() else vm.downloadUpdate() },
                contentAlignment = Alignment.Center
            ) {
                if (downloading) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(vm.updateProgress.coerceIn(0.02f, 1f)).background(cs.primary).align(Alignment.CenterStart))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!downloading) Icon(Icons.Rounded.Download, null, tint = cs.onPrimary)
                    Text(
                        when {
                            downloading -> "Downloading ${(vm.updateProgress * 100).toInt()}%"
                            ready -> "Install update"
                            vm.updateError != null -> "Retry download"
                            else -> "Download Update"
                        },
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = cs.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(CircleShape)
                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
                    .clickable { vm.dismissUpdate() },
                contentAlignment = Alignment.Center
            ) {
                Text("Later", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC9CCDB))
            }
        }
    }
}

private fun Color.blendOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f
    )
}
