package com.shinemusic.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/** Single ExoPlayer shared by the UI and the background PlaybackService. */
object PlayerHolder {
    @Volatile private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer =
        player ?: synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true
                )
                .setHandleAudioBecomingNoisy(true)
                .build().also { player = it; PlaybackStore.attach(context, it) }
        }
}
