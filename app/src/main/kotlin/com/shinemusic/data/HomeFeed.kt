package com.shinemusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The four rotating Home sections. Quick picks / "Fresh on Spotify" are handled separately. */
enum class Mood(val title: String) {
    PUNJABI("Punjabi vibes"),
    ROMANTIC("Romantic"),
    PARTY("Party mode"),
    CHILL("Chill & lo-fi")
}

/** Query pools. Every refresh picks different queries from these, so Home never looks the same twice. */
object HomeFeed {
    private val pools = mapOf(
        Mood.PUNJABI to listOf(
            "punjabi hits", "new punjabi songs", "latest punjabi 2026", "diljit dosanjh", "karan aujla",
            "ap dhillon", "punjabi romantic", "punjabi party songs", "sidhu moose wala", "gippy grewal"
        ),
        Mood.ROMANTIC to listOf(
            "romantic hindi", "hindi love songs", "arijit singh", "love songs bollywood", "atif aslam",
            "jubin nautiyal", "new romantic songs 2026", "romantic mashup", "shreya ghoshal", "darshan raval"
        ),
        Mood.PARTY to listOf(
            "party songs", "bollywood party", "punjabi party", "badshah", "yo yo honey singh",
            "dance hits hindi", "club songs hindi", "wedding songs hindi", "party anthems bollywood"
        ),
        Mood.CHILL to listOf(
            "lofi chill", "hindi lofi", "chill hindi songs", "lofi sleep", "relaxing songs hindi",
            "acoustic hindi", "study lofi", "slowed reverb hindi"
        )
    )

    /** Spotify-style queries for the Quick picks carousel. */
    val QUICK = listOf(
        "year:2026 genre:bollywood", "year:2025-2026 genre:indian", "new hindi songs", "trending hindi songs",
        "latest bollywood hits", "top bollywood songs", "arijit singh", "diljit dosanjh"
    )

    val FRESH = listOf(
        "new hindi songs", "latest punjabi songs", "romantic hindi", "new bollywood songs 2026", "new punjabi 2026"
    )

    fun pool(m: Mood): List<String> = pools.getValue(m)

    /** Time of day nudges which sections come first. */
    fun timeBoost(m: Mood, hour: Int): Double = when (hour) {
        in 5..11 -> when (m) { Mood.CHILL -> 0.3; Mood.ROMANTIC -> 0.15; else -> 0.0 }
        in 12..16 -> if (m == Mood.PUNJABI) 0.15 else 0.0
        in 17..21 -> when (m) { Mood.ROMANTIC, Mood.PARTY -> 0.2; else -> 0.0 }
        else -> if (m == Mood.CHILL) 0.4 else 0.0
    }
}

/**
 * What the user actually plays: counts per mood and per artist (saved in SharedPreferences).
 * Home re-orders its sections and builds Quick picks from this.
 */
object Taste {
    class Profile(
        val moods: MutableMap<String, Int> = HashMap(),
        val artists: MutableMap<String, Int> = HashMap()
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("shine", 0)

    fun load(c: Context): Profile = runCatching {
        val o = JSONObject(prefs(c).getString("taste", null) ?: return Profile())
        fun read(k: String): MutableMap<String, Int> {
            val m = HashMap<String, Int>()
            o.optJSONObject(k)?.let { j -> j.keys().forEach { key -> m[key] = j.optInt(key) } }
            return m
        }
        Profile(read("m"), read("a"))
    }.getOrDefault(Profile())

    fun save(c: Context, p: Profile) {
        val o = JSONObject()
            .put("m", JSONObject(p.moods as Map<*, *>))
            .put("a", JSONObject(p.artists as Map<*, *>))
        prefs(c).edit().putString("taste", o.toString()).apply()
    }

    fun record(p: Profile, s: Song, mood: Mood?) {
        mood?.let { p.moods.merge(it.name, 1, Int::plus) }
        s.artist.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(2)
            .forEach { p.artists.merge(it, 1, Int::plus) }
        // slowly forget old habits so the feed follows what you play now
        if (p.moods.values.sum() > 150) p.moods.replaceAll { _, v -> v / 2 }
        if (p.artists.values.sum() > 300) {
            p.artists.replaceAll { _, v -> v / 2 }
            p.artists.entries.removeAll { it.value <= 0 }
        }
    }

    /** Share of plays per mood (0..1). Empty until there are at least 3 classified plays. */
    fun weights(p: Profile): Map<Mood, Double> {
        val total = p.moods.values.sum()
        if (total < 3) return emptyMap()
        return Mood.values().associateWith { (p.moods[it.name] ?: 0).toDouble() / total }
    }

    fun topArtists(p: Profile, n: Int): List<String> =
        p.artists.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(n).map { it.key }

    private val keywords = listOf(
        Mood.CHILL to listOf("lofi", "lo fi", "chill", "sleep", "relax", "relaxing", "slowed", "reverb", "study", "acoustic"),
        Mood.PARTY to listOf("party", "dj", "club", "bass", "dance", "badshah", "honey singh", "wedding"),
        Mood.PUNJABI to listOf("punjabi", "diljit", "karan aujla", "sidhu moose wala", "ap dhillon", "shubh", "amrit maan", "jass manak", "gippy grewal"),
        Mood.ROMANTIC to listOf("love", "ishq", "pyar", "dil", "romantic", "mohabbat", "arijit singh", "atif aslam", "jubin nautiyal", "darshan raval", "tum")
    )

    /** Best-effort mood from title + artist. Null when nothing matches. */
    fun classify(s: Song): Mood? {
        val t = " ${normText(s.title + " " + s.artist)} "
        return keywords.firstOrNull { (_, words) -> words.any { t.contains(" $it ") } }?.first
    }
}

/** Saves the last Home screen so the app opens instantly and refreshes in the background. */
object HomeCache {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("shine", 0)

    fun save(c: Context, sections: List<Pair<String, List<Song>>>) {
        val arr = JSONArray()
        sections.forEach { (title, songs) ->
            val sa = JSONArray()
            songs.forEach {
                sa.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist)
                    .put("i", it.image).put("u", it.url).put("d", it.durationSec))
            }
            arr.put(JSONObject().put("title", title).put("songs", sa))
        }
        prefs(c).edit().putString("home_cache", arr.toString())
            .putLong("home_cache_ts", System.currentTimeMillis()).apply()
    }

    /** Cached sections and the time they were saved, or null. */
    fun load(c: Context): Pair<List<Pair<String, List<Song>>>, Long>? = runCatching {
        val p = prefs(c)
        val arr = JSONArray(p.getString("home_cache", null) ?: return null)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sa = o.getJSONArray("songs")
            o.getString("title") to (0 until sa.length()).map { j ->
                val s = sa.getJSONObject(j)
                Song(s.getString("id"), s.getString("t"), s.getString("a"), s.getString("i"), s.getString("u"), s.optInt("d"))
            }
        }.filter { it.second.isNotEmpty() }
        if (list.isEmpty()) null else list to p.getLong("home_cache_ts", 0L)
    }.getOrNull()
}
