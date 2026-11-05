package com.shinemusic.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shinemusic.MusicViewModel
import com.shinemusic.data.AppSettings
import com.shinemusic.data.Downloads
import com.shinemusic.player.PlayerEffects
import com.shinemusic.ui.components.GlassIconButton
import com.shinemusic.ui.components.glass
import kotlin.math.roundToInt

/** Full-screen settings. Content is centred with a max width, so it also looks right on tablets / landscape. */
@Composable
fun SettingsScreen(vm: MusicViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack, contentDescription = "Back")
                Spacer(Modifier.width(14.dp))
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
            PlaybackSection()
            DownloadsSection(vm)
            AppearanceSection()
            AboutSection()
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ------------------------------------------------------------------------- sections

@Composable
private fun PlaybackSection() {
    Section("Playback") {
        ChoiceRow(
            "Audio quality", listOf("Data saver", "Normal", "High"), AppSettings.audioQuality,
            hint = when (AppSettings.audioQuality) {
                0 -> "About 48 kbps - uses the least data"
                1 -> "About 160 kbps - balanced"
                else -> "Up to 320 kbps - best sound"
            }
        ) { AppSettings.setAudioQuality(it) }
        Divider()
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        ChoiceRow(
            "Playback speed", speeds.map { if (it == 1f) "Normal" else "${it}x" },
            speeds.indexOfFirst { kotlin.math.abs(it - AppSettings.speed) < 0.01f }
        ) { AppSettings.setSpeed(speeds[it]) }
        Divider()
        SliderRow(
            "Crossfade", if (AppSettings.crossfadeSec == 0) "Off" else "${AppSettings.crossfadeSec} s",
            AppSettings.crossfadeSec.toFloat(), 0f..12f, steps = 11,
            hint = "Fades the end of a song out and the next one in"
        ) { AppSettings.setCrossfade(it.roundToInt()) }
        Divider()
        SwitchRow("Skip silence", "Jump over quiet gaps in songs", AppSettings.skipSilence) { AppSettings.setSkipSilence(it) }
        SwitchRow("Volume normalization", "Evens out quiet songs", AppSettings.normalize) { AppSettings.setNormalize(it) }
        SwitchRow("Resume last song", "Open the app on your last song and position", AppSettings.resumeLast) { AppSettings.setResumeLast(it) }
        SwitchRow("Pause when headphones unplug", null, AppSettings.pauseOnUnplug) { AppSettings.setPauseOnUnplug(it) }
        Divider()
        EqualizerBlock()
    }
}

@Composable
private fun EqualizerBlock() {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Equalizer & bass boost", style = MaterialTheme.typography.titleMedium)
            Text(
                if (AppSettings.eqEnabled || AppSettings.bassBoost > 0) "On" else "Off",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant)
    }
    AnimatedVisibility(open) {
        Column {
            SwitchRow("Equalizer", null, AppSettings.eqEnabled) { AppSettings.setEqEnabled(it) }
            if (PlayerEffects.bandCount > 0) {
                val names = PlayerEffects.presets + "Custom"
                val sel = if (AppSettings.eqPreset in PlayerEffects.presets.indices) AppSettings.eqPreset else names.lastIndex
                ChoiceRow("Preset", names, sel) { PlayerEffects.selectPreset(it) }
                for (b in 0 until PlayerEffects.bandCount) {
                    val hz = PlayerEffects.centerHz.getOrNull(b) ?: 0
                    val lvl = PlayerEffects.level(b)
                    SliderRow(
                        if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", "${"%.1f".format(lvl / 100f)} dB", lvl.toFloat(),
                        PlayerEffects.minLevel.toFloat()..PlayerEffects.maxLevel.toFloat()
                    ) { PlayerEffects.setBand(b, it.roundToInt()) }
                }
            } else {
                Text(
                    "Equalizer is not available on this device",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
            SliderRow(
                "Bass boost", if (AppSettings.bassBoost == 0) "Off" else "${AppSettings.bassBoost / 10}%",
                AppSettings.bassBoost.toFloat(), 0f..1000f
            ) { AppSettings.setBassBoost(it.roundToInt()) }
        }
    }
}

@Composable
private fun DownloadsSection(vm: MusicViewModel) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    val count = Downloads.songs.size
    val bytes = remember(count, Downloads.progress.size) { Downloads.totalBytes(ctx) }

    Section("Downloads") {
        ChoiceRow(
            "Download quality", listOf("Data saver", "Normal", "High"), AppSettings.downloadQuality,
            hint = "Applies to new downloads. Higher quality uses more storage"
        ) { AppSettings.setDownloadQuality(it) }
        Divider()
        SwitchRow("Download on Wi-Fi only", "Won't download on mobile data", AppSettings.wifiOnly) { AppSettings.setWifiOnly(it) }
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Storage used", style = MaterialTheme.typography.titleMedium)
                Text(
                    "$count songs - ${Formatter.formatShortFileSize(ctx, bytes)}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            TextButton(enabled = count > 0, onClick = { confirm = true }) { Text("Clear downloads") }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear downloads?") },
            text = { Text("All $count downloaded songs will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.clearDownloads() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AppearanceSection() {
    val cs = MaterialTheme.colorScheme
    Section("Appearance") {
        ChoiceRow("Theme", listOf("Dark", "Light", "System"), AppSettings.themeMode) { AppSettings.setThemeMode(it) }
        Divider()
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text("Accent colour", style = MaterialTheme.typography.titleMedium)
            if (AppSettings.dynamicColor) {
                Text(
                    "Album art colour is in use right now",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppSettings.accents.forEachIndexed { i, (_, color) ->
                    val sel = i == AppSettings.accentIndex
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(color)
                            .border(if (sel) 3.dp else 1.dp, if (sel) cs.onBackground else cs.outlineVariant, CircleShape)
                            .clickable { AppSettings.setAccent(i) },
                        contentAlignment = Alignment.Center
                    ) { if (sel) Icon(Icons.Rounded.Check, "Selected", tint = Color.Black.copy(alpha = 0.7f)) }
                }
            }
        }
        Divider()
        SwitchRow("Colours from album art", "App colours follow the song that is playing", AppSettings.dynamicColor) { AppSettings.setDynamicColor(it) }
        SwitchRow("Blurred background", "Soft colour glow behind the screens", AppSettings.blurBackground) { AppSettings.setBlurBackground(it) }
        SwitchRow("Reduce animations", "Smoother on older phones", AppSettings.reduceAnimations) { AppSettings.setReduceAnimations(it) }
    }
}

@Composable
private fun AboutSection() {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-"
    }
    Section("About") {
        InfoRow("App", "ShineMusic")
        InfoRow("Version", version)
        InfoRow("Developer", "mrshinee")
        InfoRow("Source code", "github.com/mrshinee/ShineMusic") {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mrshinee/ShineMusic")))
            }
        }
    }
}

// ------------------------------------------------------------------------- building blocks

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp)
    )
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(vertical = 6.dp), content = content)
}

@Composable
private fun Divider() {
    Box(
        Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, hint: String? = null, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) cs.primary else cs.surfaceVariant)
                        .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) cs.onPrimary else cs.onSurface)
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>,
    steps: Int = 0, hint: String? = null, onChange: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) cs.primary else cs.onSurfaceVariant
        )
    }
}
