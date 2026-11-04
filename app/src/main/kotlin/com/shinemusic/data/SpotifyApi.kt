package com.shinemusic.data

import android.util.Base64
import com.shinemusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Spotify Web API (Client Credentials flow - no user login needed).
 *
 * Spotify only supplies METADATA (accurate titles, artists, HD cover art). It does not provide
 * full-song audio, so every Spotify track is matched to a playable stream on the Saavn API
 * ([resolve]); tracks that can't be matched confidently are dropped, never played wrongly.
 *
 * Built around the Feb-2026 dev-mode rules: search is capped at 10 per call (we page),
 * and every failure (rate limit, quota, expired Premium, offline) degrades silently to
 * "no Spotify results" so the app keeps working on Saavn alone.
 */
object SpotifyApi {

    private const val CACHE_MS = 10 * 60 * 1000L

    val enabled: Boolean
        get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank() && BuildConfig.SPOTIFY_CLIENT_SECRET.isNotBlank()

    private class Track(
        val id: String,
        val title: String,
        val artists: List<String>,
        val image: String,
        val durationMs: Long
    )

    private class Http(val code: Int, val body: String, val retryAfterSec: Long)

    private val tokenLock = Mutex()
    @Volatile private var token: String? = null
    @Volatile private var tokenExpiry = 0L
    @Volatile private var pausedUntil = 0L          // circuit breaker for 429 / 403

    private val cache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()
    private val resolved = ConcurrentHashMap<String, Song>()
    private val unresolved = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(5)                  // max parallel Saavn look-ups

    private val unwanted = Regex("remix|cover|lofi|lo-fi|slowed|reverb|karaoke|instrumental|8d|sped up|mashup")

    // ------------------------------------------------------------------ public

    /** Spotify-ranked, stream-ready songs for [query]. Empty if Spotify is off, limited or offline. */
    suspend fun searchSongs(query: String, total: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (!enabled || q.isEmpty()) return@withContext emptyList()

        val key = "${q.lowercase()}|$total"
        cache[key]?.let { (t, list) ->
            if (System.currentTimeMillis() - t < CACHE_MS) return@withContext list
        }

        val pages = ((total + 9) / 10).coerceIn(1, 3)
        val tracks = coroutineScope {
            (0 until pages).map { p -> async { fetchPage(q, p * 10) } }.awaitAll().flatten()
        }.distinctBy { it.id }
        if (tracks.isEmpty()) return@withContext emptyList()

        val songs = coroutineScope {
            tracks.map { t -> async { gate.withPermit { resolve(t) } } }.awaitAll()
        }.filterNotNull().uniqueSongs()

        if (songs.isNotEmpty()) cache[key] = System.currentTimeMillis() to songs
        songs
    }

    // ------------------------------------------------------------------ http

    private fun request(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), form: String? = null): Http {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 12000
            c.readTimeout = 15000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (form != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Http(code, body, c.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 0L)
        } finally {
            c.disconnect()
        }
    }

    private suspend fun accessToken(force: Boolean = false): String? = tokenLock.withLock {
        val cached = token
        if (!force && cached != null && System.currentTimeMillis() < tokenExpiry) return@withLock cached

        val basic = Base64.encodeToString(
            "${BuildConfig.SPOTIFY_CLIENT_ID}:${BuildConfig.SPOTIFY_CLIENT_SECRET}".toByteArray(), Base64.NO_WRAP
        )
        val r = runCatching {
            request(
                "https://accounts.spotify.com/api/token", "POST",
                mapOf("Authorization" to "Basic $basic"), "grant_type=client_credentials"
            )
        }.getOrNull()
        if (r == null || r.code != 200) {
            if (r?.code == 429) pausedUntil = System.currentTimeMillis() + max(r.retryAfterSec, 10L) * 1000
            return@withLock null
        }
        val o = runCatching { JSONObject(r.body) }.getOrNull() ?: return@withLock null
        token = o.optString("access_token").ifEmpty { null }
        tokenExpiry = System.currentTimeMillis() + (o.optLong("expires_in", 3600L) - 60L) * 1000
        token
    }

    private suspend fun fetchPage(q: String, offset: Int): List<Track> {
        if (System.currentTimeMillis() < pausedUntil) return emptyList()
        // Dev mode: limit max is 10 (Feb 2026), so we always ask for exactly 10 and page with offset.
        val url = "https://api.spotify.com/v1/search?q=${URLEncoder.encode(q, "UTF-8")}&type=track&limit=10&offset=$offset"

        suspend fun call(tok: String) = runCatching { request(url, headers = mapOf("Authorization" to "Bearer $tok")) }.getOrNull()

        var r = call(accessToken() ?: return emptyList()) ?: return emptyList()
        if (r.code == 401) r = call(accessToken(force = true) ?: return emptyList()) ?: return emptyList()
        when (r.code) {
            200 -> Unit
            429 -> { pausedUntil = System.currentTimeMillis() + max(r.retryAfterSec, 5L) * 1000; return emptyList() }
            403 -> { pausedUntil = System.currentTimeMillis() + 10 * 60 * 1000L; return emptyList() } // quota / Premium / allowlist
            else -> return emptyList()
        }
        return runCatching { parseTracks(r.body) }.getOrDefault(emptyList())
    }

    private fun parseTracks(body: String): List<Track> {
        val items = JSONObject(body).optJSONObject("tracks")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Track>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue          // Spotify can return null entries
            val id = o.optString("id")
            val title = o.optString("name")
            if (id.isEmpty() || title.isEmpty()) continue
            val artists = o.optJSONArray("artists")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
            }.orEmpty()
            val image = o.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()
            out.add(Track(id, title, artists, image, o.optLong("duration_ms", 0L)))
        }
        return out
    }

    // ------------------------------------------------------------------ matching

    /** Find the playable Saavn version of a Spotify track, or null when no confident match exists. */
    private suspend fun resolve(t: Track): Song? {
        resolved[t.id]?.let { return it }
        if (t.id in unresolved) return null

        val wantTitle = normTitle(t.title)
        val wantArtists = t.artists.map { normText(it) }.filter { it.isNotEmpty() }
        if (wantTitle.isEmpty()) return null
        val wantsVariant = unwanted.containsMatchIn(t.title.lowercase())

        val cands = SaavnApi.search("${t.title} ${t.artists.firstOrNull().orEmpty()}", 8)
        var best: Song? = null
        var bestScore = 0.0
        for (c in cands) {
            val ct = normTitle(c.title)
            if (ct.isEmpty()) continue
            val titleScore = when {
                ct == wantTitle -> 3.0
                ct.contains(wantTitle) || wantTitle.contains(ct) -> 1.5
                else -> continue
            }
            val ca = normText(c.artist)
            val artistHit = ca.isNotEmpty() && wantArtists.any { ca.contains(it) || it.contains(ca) }
            val durHit = t.durationMs > 0 && c.durationSec > 0 && abs(c.durationSec - t.durationMs / 1000) <= 8
            // wrong artist is only accepted for an exact title with the same length (e.g. spelling differences)
            if (!artistHit && !(titleScore == 3.0 && durHit)) continue

            var score = titleScore + (if (artistHit) 2.0 else 0.0) + (if (durHit) 1.0 else 0.0)
            if (!wantsVariant && unwanted.containsMatchIn(c.title.lowercase())) score -= 2.5
            if (score > bestScore) { bestScore = score; best = c }
        }

        val hit = best
        if (hit == null || bestScore < 3.0) { unresolved.add(t.id); return null }
        val song = Song(
            id = hit.id,
            title = t.title,
            artist = t.artists.joinToString(", ").ifEmpty { hit.artist },
            image = t.image.ifEmpty { hit.image },      // Spotify cover = 640px
            url = hit.url,
            durationSec = hit.durationSec
        )
        resolved[t.id] = song
        return song
    }
}
