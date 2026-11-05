package com.shinemusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * All user settings. Every value is Compose state (the UI reacts instantly) and is saved in
 * SharedPreferences "shine_settings". Call [init] once (MainActivity / PlayerHolder do it).
 */
object AppSettings {
    private var sp: SharedPreferences? = null

    val accents: List<Pair<String, Color>> = listOf(
        "Indigo" to Color(0xFF9FB0FF), "Orchid" to Color(0xFFFF9AD1), "Emerald" to Color(0xFF6EE7B7),
        "Amber" to Color(0xFFFFC857), "Sky" to Color(0xFF7DD3FC), "Coral" to Color(0xFFFF8A80),
        "Violet" to Color(0xFFC4A1FF)
    )

    // ---- playback
    var audioQuality by mutableStateOf(2); private set        // 0 data saver, 1 normal, 2 high
    var speed by mutableStateOf(1f); private set
    var crossfadeSec by mutableStateOf(0); private set        // 0 = off
    var skipSilence by mutableStateOf(false); private set
    var normalize by mutableStateOf(false); private set
    var eqEnabled by mutableStateOf(false); private set
    var eqPreset by mutableStateOf(-1); private set           // -1 = custom
    var eqLevels by mutableStateOf<List<Int>>(emptyList()); private set
    var bassBoost by mutableStateOf(0); private set           // 0..1000
    var resumeLast by mutableStateOf(true); private set
    var pauseOnUnplug by mutableStateOf(true); private set

    // ---- downloads
    var downloadQuality by mutableStateOf(1); private set
    var wifiOnly by mutableStateOf(false); private set

    // ---- appearance
    var themeMode by mutableStateOf(0); private set           // 0 dark, 1 light, 2 system
    var accentIndex by mutableStateOf(0); private set
    var dynamicColor by mutableStateOf(false); private set
    var blurBackground by mutableStateOf(true); private set
    var reduceAnimations by mutableStateOf(false); private set

    @Synchronized
    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("shine_settings", 0)
        sp = p
        audioQuality = p.getInt("quality", 2)
        speed = p.getFloat("speed", 1f)
        crossfadeSec = p.getInt("crossfade", 0)
        skipSilence = p.getBoolean("skip_silence", false)
        normalize = p.getBoolean("normalize", false)
        eqEnabled = p.getBoolean("eq_on", false)
        eqPreset = p.getInt("eq_preset", -1)
        eqLevels = p.getString("eq_levels", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
        bassBoost = p.getInt("bass", 0)
        resumeLast = p.getBoolean("resume_last", true)
        pauseOnUnplug = p.getBoolean("pause_unplug", true)
        downloadQuality = p.getInt("dl_quality", 1)
        wifiOnly = p.getBoolean("wifi_only", false)
        themeMode = p.getInt("theme", 0)
        accentIndex = p.getInt("accent", 0)
        dynamicColor = p.getBoolean("dynamic", false)
        blurBackground = p.getBoolean("blur", true)
        reduceAnimations = p.getBoolean("reduce_anim", false)
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) { sp?.edit()?.apply(block)?.apply() }

    fun setAudioQuality(v: Int) { audioQuality = v; put { putInt("quality", v) } }
    fun setSpeed(v: Float) { speed = v; put { putFloat("speed", v) } }
    fun setCrossfade(v: Int) { crossfadeSec = v; put { putInt("crossfade", v) } }
    fun setSkipSilence(v: Boolean) { skipSilence = v; put { putBoolean("skip_silence", v) } }
    fun setNormalize(v: Boolean) { normalize = v; put { putBoolean("normalize", v) } }
    fun setEqEnabled(v: Boolean) { eqEnabled = v; put { putBoolean("eq_on", v) } }
    fun setEqPreset(v: Int) { eqPreset = v; put { putInt("eq_preset", v) } }
    fun setEq(enabled: Boolean, preset: Int, levels: List<Int>) {
        eqEnabled = enabled; eqPreset = preset; eqLevels = levels
        put { putBoolean("eq_on", enabled); putInt("eq_preset", preset); putString("eq_levels", levels.joinToString(",")) }
    }
    fun setBassBoost(v: Int) { bassBoost = v; put { putInt("bass", v) } }
    fun setResumeLast(v: Boolean) { resumeLast = v; put { putBoolean("resume_last", v) } }
    fun setPauseOnUnplug(v: Boolean) { pauseOnUnplug = v; put { putBoolean("pause_unplug", v) } }
    fun setDownloadQuality(v: Int) { downloadQuality = v; put { putInt("dl_quality", v) } }
    fun setWifiOnly(v: Boolean) { wifiOnly = v; put { putBoolean("wifi_only", v) } }
    fun setThemeMode(v: Int) { themeMode = v; put { putInt("theme", v) } }
    fun setAccent(v: Int) { accentIndex = v; put { putInt("accent", v) } }
    fun setDynamicColor(v: Boolean) { dynamicColor = v; put { putBoolean("dynamic", v) } }
    fun setBlurBackground(v: Boolean) { blurBackground = v; put { putBoolean("blur", v) } }
    fun setReduceAnimations(v: Boolean) { reduceAnimations = v; put { putBoolean("reduce_anim", v) } }

    fun accentColor(): Color = accents.getOrNull(accentIndex)?.second ?: accents[0].second

    private val kbpsRe = Regex("_(12|48|96|160|320)\\.(mp4|m4a|mp3)")

    /** Saavn links carry the bitrate in the file name (_320.mp4). 0 = 48 kbps, 1 = 160 kbps, 2 = leave as is (best). */
    fun urlFor(url: String, level: Int): String {
        val kbps = when (level) { 0 -> 48; 1 -> 160; else -> return url }
        return kbpsRe.replace(url) { "_$kbps.${it.groupValues[2]}" }
    }
}
