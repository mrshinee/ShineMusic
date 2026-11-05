package com.shinemusic.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.shinemusic.data.AppSettings

/** Applies playback settings (speed, skip silence, unplug, equalizer, bass boost, normalization) to the player. */
object PlayerEffects {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var session = -1

    var bandCount by mutableStateOf(0); private set
    var minLevel by mutableStateOf(-1500); private set
    var maxLevel by mutableStateOf(1500); private set
    var centerHz by mutableStateOf<List<Int>>(emptyList()); private set
    var presets by mutableStateOf<List<String>>(emptyList()); private set

    fun install(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                attach(audioSessionId)
                apply(player)
            }
        })
        attach(player.audioSessionId)
        apply(player)
    }

    private fun attach(id: Int) {
        if (id == C.AUDIO_SESSION_ID_UNSET || id == session) return
        release()
        session = id
        eq = runCatching { Equalizer(0, id) }.getOrNull()
        bass = runCatching { BassBoost(0, id) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(id) }.getOrNull()
        eq?.let { e ->
            runCatching {
                bandCount = e.numberOfBands.toInt()
                val r = e.bandLevelRange
                minLevel = r[0].toInt()
                maxLevel = r[1].toInt()
                centerHz = (0 until bandCount).map { e.getCenterFreq(it.toShort()) / 1000 }
                presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            }
        }
    }

    private fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { loud?.release() }
        eq = null; bass = null; loud = null
    }

    fun apply(player: ExoPlayer) {
        runCatching {
            player.setPlaybackParameters(PlaybackParameters(AppSettings.speed))
            player.setSkipSilenceEnabled(AppSettings.skipSilence)
            player.setHandleAudioBecomingNoisy(AppSettings.pauseOnUnplug)
        }
        runCatching {
            eq?.let { e ->
                e.setEnabled(AppSettings.eqEnabled)
                if (AppSettings.eqEnabled) {
                    val p = AppSettings.eqPreset
                    if (p in 0 until presets.size) e.usePreset(p.toShort())
                    else AppSettings.eqLevels.forEachIndexed { i, l ->
                        if (i < bandCount) e.setBandLevel(i.toShort(), l.coerceIn(minLevel, maxLevel).toShort())
                    }
                }
            }
        }
        runCatching {
            bass?.let { b ->
                b.setEnabled(AppSettings.bassBoost > 0)
                if (b.strengthSupported) b.setStrength(AppSettings.bassBoost.toShort())
            }
        }
        runCatching {
            loud?.let { l ->
                l.setTargetGain(300)          // +3 dB: evens out quiet songs
                l.setEnabled(AppSettings.normalize)
            }
        }
    }

    fun level(band: Int): Int =
        AppSettings.eqLevels.getOrNull(band) ?: runCatching { eq?.getBandLevel(band.toShort())?.toInt() }.getOrNull() ?: 0

    fun selectPreset(i: Int) {
        val e = eq
        if (e != null && i in 0 until presets.size) {
            runCatching {
                e.setEnabled(true)
                e.usePreset(i.toShort())
                AppSettings.setEq(true, i, (0 until bandCount).map { e.getBandLevel(it.toShort()).toInt() })
            }
        } else {
            AppSettings.setEqPreset(-1)
        }
    }

    fun setBand(band: Int, value: Int) {
        val levels = (0 until bandCount).map { level(it) }.toMutableList()
        if (band !in levels.indices) return
        levels[band] = value
        AppSettings.setEq(AppSettings.eqEnabled, -1, levels)
    }
}
