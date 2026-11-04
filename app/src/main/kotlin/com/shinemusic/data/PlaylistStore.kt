package com.shinemusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A user-created playlist. Songs are unique by id inside one playlist. */
data class Playlist(val id: String, val name: String, val songs: List<Song> = emptyList())

/** Saves playlists as JSON in SharedPreferences ("shine" / "playlists"). */
object PlaylistStore {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("shine", 0)

    @Synchronized
    fun load(c: Context): List<Playlist> {
        val json = prefs(c).getString("playlists", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sa = o.optJSONArray("songs") ?: JSONArray()
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    songs = (0 until sa.length()).map { j ->
                        val s = sa.getJSONObject(j)
                        Song(s.getString("id"), s.getString("t"), s.getString("a"), s.getString("i"), s.getString("u"), s.optInt("d"))
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(c: Context, list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val sa = JSONArray()
            p.songs.forEach {
                sa.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist)
                    .put("i", it.image).put("u", it.url).put("d", it.durationSec))
            }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", sa))
        }
        prefs(c).edit().putString("playlists", arr.toString()).apply()
    }
}
