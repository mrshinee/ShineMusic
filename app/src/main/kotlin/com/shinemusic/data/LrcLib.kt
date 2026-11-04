package com.shinemusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Synced lyrics from the free lrclib.net service. */
object LrcLib {
    suspend fun fetch(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        try {
            fun e(s: String) = URLEncoder.encode(s, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=${e(song.title)}" +
                "&artist_name=${e(song.artist.substringBefore(','))}" +
                (if (song.durationSec > 0) "&duration=${song.durationSec}" else "")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            if (c.responseCode != 200) return@withContext emptyList()
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val synced = o.optString("syncedLyrics")
            if (synced.isNotBlank() && synced != "null") parseLrc(synced)
            else o.optString("plainLyrics").lines().filter { it.isNotBlank() }.mapIndexed { i, l -> LyricLine(-1L - i, l) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val rx = Regex("""\[(\d+):(\d+(?:\.\d+)?)](.*)""")
    private fun parseLrc(s: String) = s.lines().mapNotNull { line ->
        rx.find(line)?.let {
            val (m, sec, text) = it.destructured
            LyricLine(((m.toInt() * 60 + sec.toDouble()) * 1000).toLong(), text.trim())
        }
    }.filter { it.text.isNotEmpty() }
}
