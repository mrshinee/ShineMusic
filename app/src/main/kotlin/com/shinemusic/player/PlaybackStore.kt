package com.shinemusic.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the queue, current song, position, shuffle and repeat mode.
 *
 * Why: when Android (or vivo's battery manager) kills the app process and later restarts the
 * music service, the player used to come back EMPTY - the lock-screen card / island then showed
 * only "ShineMusic" with a play button that did nothing. Now the player is refilled from this
 * saved state the moment it is created, so the card always shows the last song and play resumes it.
 */
object PlaybackStore {
    private const val PREFS = "shine_playback"
    private const val MAX_ITEMS = 150

    fun attach(context: Context, player: Player) {
        val app = context.applicationContext
        restore(app, player)
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY
                    )
                ) save(app, p)
            }
        })
    }

    fun save(context: Context, player: Player) {
        val n = player.mediaItemCount
        if (n == 0) return
        // keep a window of up to MAX_ITEMS around the current song
        val cur = player.currentMediaItemIndex
        val from = (cur - MAX_ITEMS / 3).coerceAtLeast(0)
        val to = (from + MAX_ITEMS).coerceAtMost(n)
        val arr = JSONArray()
        for (i in from until to) {
            val mi = player.getMediaItemAt(i)
            val uri = mi.localConfiguration?.uri?.toString() ?: continue
            arr.put(
                JSONObject().put("id", mi.mediaId).put("t", mi.mediaMetadata.title?.toString().orEmpty())
                    .put("a", mi.mediaMetadata.artist?.toString().orEmpty())
                    .put("i", mi.mediaMetadata.artworkUri?.toString().orEmpty()).put("u", uri)
            )
        }
        if (arr.length() == 0) return
        context.applicationContext.getSharedPreferences(PREFS, 0).edit()
            .putString("items", arr.toString())
            .putInt("index", (cur - from).coerceIn(0, arr.length() - 1))
            .putLong("pos", player.currentPosition.coerceAtLeast(0))
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode)
            .apply()
    }

    private fun restore(context: Context, player: Player) {
        if (player.mediaItemCount > 0) return
        val sp = context.getSharedPreferences(PREFS, 0)
        val json = sp.getString("items", null) ?: return
        runCatching {
            val a = JSONArray(json)
            val items = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                MediaItem.Builder()
                    .setMediaId(o.getString("id"))
                    .setUri(o.getString("u"))
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(o.optString("t")).setArtist(o.optString("a"))
                            .apply { o.optString("i").takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) } }
                            .build()
                    ).build()
            }
            if (items.isEmpty()) return
            val index = sp.getInt("index", 0).coerceIn(0, items.size - 1)
            player.repeatMode = sp.getInt("repeat", Player.REPEAT_MODE_ALL)
            player.shuffleModeEnabled = sp.getBoolean("shuffle", false)
            player.setMediaItems(items, index, sp.getLong("pos", 0L))
            player.playWhenReady = false          // show the last song, don't start playing by itself
            player.prepare()
        }
    }
}
