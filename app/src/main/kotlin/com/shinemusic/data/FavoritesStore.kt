package com.shinemusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites shared by the app UI and the media notification / lock-screen button.
 * Same SharedPreferences key as before ("shine" / "fav") so existing favorites are kept.
 * [version] changes after every write, so any screen or service can refresh itself.
 */
object FavoritesStore {
    val version = MutableStateFlow(0)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("shine", 0)

    @Synchronized
    fun load(c: Context): List<Song> {
        val json = prefs(c).getString("fav", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(json)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Song(o.getString("id"), o.getString("t"), o.getString("a"), o.getString("i"), o.getString("u"), o.optInt("d"))
            }
        }.getOrDefault(emptyList())
    }

    fun isFav(c: Context, id: String?): Boolean = id != null && load(c).any { it.id == id }

    /** @return true if the song is now a favorite */
    @Synchronized
    fun toggle(c: Context, song: Song): Boolean {
        val list = load(c).toMutableList()
        val i = list.indexOfFirst { it.id == song.id }
        val nowFav = i < 0
        if (nowFav) list.add(0, song) else list.removeAt(i)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist)
                .put("i", it.image).put("u", it.url).put("d", it.durationSec))
        }
        prefs(c).edit().putString("fav", arr.toString()).apply()
        version.value = version.value + 1
        return nowFav
    }
}
