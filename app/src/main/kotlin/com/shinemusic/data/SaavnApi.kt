package com.shinemusic.data

import com.shinemusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Tolerant JioSaavn-style API client (supports both saavn.dev and older response shapes).
 *
 *  - retries once per server, then falls through to the fallback servers
 *  - never returns duplicate songs (same id, or same title + artist under a different id)
 *  - short in-memory cache so the same query isn't re-downloaded (and can't flip between results)
 */
object SaavnApi {

    private const val CACHE_MS = 10 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()

    private val bases: List<String> =
        (listOf(BuildConfig.API_BASE) + BuildConfig.API_FALLBACKS.split(",").map { it.trim() })
            .filter { it.isNotEmpty() }
            .map { it.trimEnd('/') }
            .distinct()

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 18000
        c.setRequestProperty("User-Agent", "ShineMusic/1.0")
        c.setRequestProperty("Accept", "application/json")
        return try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun search(query: String, limit: Int = 20, page: Int = 0): List<Song> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()

        val cacheKey = "${q.lowercase()}|$limit|$page"
        cache[cacheKey]?.let { (t, list) ->
            if (System.currentTimeMillis() - t < CACHE_MS) return@withContext list
        }

        val enc = URLEncoder.encode(q, "UTF-8")
        val pageParam = if (page > 0) "&page=$page" else ""
        for (base in bases) {
            var attempt = 0
            while (attempt < 2) {
                attempt++
                val res = runCatching { parseList(get("$base/api/search/songs?query=$enc&limit=$limit$pageParam")) }
                val list = res.getOrNull()
                if (!list.isNullOrEmpty()) {
                    cache[cacheKey] = System.currentTimeMillis() to list
                    return@withContext list
                }
                if (res.isSuccess) break   // server answered "nothing found": retrying won't help
            }
        }
        emptyList()
    }

    private fun parseList(body: String): List<Song> {
        val root = JSONObject(body)
        val data = root.optJSONObject("data")
        val arr = data?.optJSONArray("results")
            ?: data?.optJSONArray("songs")
            ?: root.optJSONArray("results")
            ?: JSONArray()
        return (0 until arr.length()).mapNotNull { parse(arr.optJSONObject(it)) }.uniqueSongs()
    }

    private fun bestUrl(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        // highest quality is last; walk backwards until a real link is found
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("url").ifEmpty { o.optString("link") }
            if (u.isNotEmpty()) return u
        }
        return ""
    }

    private fun https(u: String) = if (u.startsWith("http://")) "https://" + u.removePrefix("http://") else u

    private fun parse(o: JSONObject?): Song? {
        if (o == null) return null
        val id = o.optString("id")
        val title = clean(o.optString("name").ifEmpty { o.optString("title") })
        val img = when (val i = o.opt("image")) {
            is JSONArray -> bestUrl(i)
            is String -> i.replace("150x150", "500x500").replace("50x50", "500x500")
            else -> ""
        }
        val url = when (val d = o.opt("downloadUrl")) {
            is JSONArray -> bestUrl(d)
            is String -> d
            else -> ""
        }
        val artist = o.optJSONObject("artists")?.optJSONArray("primary")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
                .joinToString(", ")
        }.orEmpty().ifBlank { o.optString("primaryArtists") }
        if (id.isEmpty() || title.isEmpty() || url.isEmpty()) return null
        return Song(id, title, clean(artist), https(img), https(url), o.optString("duration").toIntOrNull() ?: 0)
    }

    private fun clean(s: String) = s
        .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
