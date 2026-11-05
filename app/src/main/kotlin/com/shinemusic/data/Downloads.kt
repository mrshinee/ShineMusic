package com.shinemusic.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Offline songs: files in filesDir/downloads, index in SharedPreferences "shine_downloads". */
object Downloads {
    val songs = mutableStateListOf<Song>()
    val progress = mutableStateMapOf<String, Float>()    // song id -> 0..1 while downloading
    private var ready = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("shine_downloads", 0)
    private fun dir(c: Context) = File(c.applicationContext.filesDir, "downloads").apply { mkdirs() }
    fun fileFor(c: Context, id: String) = File(dir(c), id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".m4a")

    @Synchronized
    fun init(c: Context) {
        if (ready) return
        ready = true
        runCatching {
            val arr = JSONArray(prefs(c).getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val song = Song(s.getString("id"), s.getString("t"), s.getString("a"), s.getString("i"), s.getString("u"), s.optInt("d"))
                if (fileFor(c, song.id).exists()) songs.add(song)
            }
        }
    }

    private fun save(c: Context) {
        val arr = JSONArray()
        songs.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist)
                .put("i", it.image).put("u", it.url).put("d", it.durationSec))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun isDownloaded(id: String) = songs.any { it.id == id }

    /** file:// link when the song is saved on the phone, otherwise null. */
    fun localUri(c: Context, id: String): Uri? =
        if (isDownloaded(id)) fileFor(c, id).takeIf { it.exists() }?.let { Uri.fromFile(it) } else null

    fun totalBytes(c: Context): Long = dir(c).listFiles()?.filter { it.name.endsWith(".m4a") }?.sumOf { it.length() } ?: 0L

    private fun onUnmetered(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** @return null on success, otherwise a short message for the user. */
    suspend fun download(c: Context, song: Song): String? {
        val app = c.applicationContext
        init(app)
        if (isDownloaded(song.id)) return null
        if (progress.containsKey(song.id)) return "Already downloading"
        if (AppSettings.wifiOnly && !onUnmetered(app)) return "Wi-Fi only is on - connect to Wi-Fi to download"
        if (song.url.isBlank()) return "No download link for this song"

        progress[song.id] = 0f
        val target = fileFor(app, song.id)
        val part = File(target.path + ".part")
        try {
            withContext(Dispatchers.IO) {
                val conn = (URL(AppSettings.urlFor(song.url, AppSettings.downloadQuality)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ShineMusic")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(32 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) progress[song.id] = (done.toFloat() / total).coerceIn(0f, 1f)
                                ensureActive()
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (!part.renameTo(target)) error("Could not save the file")
            }
            songs.add(0, song)
            save(app)
            return null
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            return "Download failed: ${e.message ?: "unknown error"}"
        } finally {
            progress.remove(song.id)
        }
    }

    fun remove(c: Context, id: String) {
        fileFor(c, id).delete()
        if (songs.removeAll { it.id == id }) save(c)
    }

    fun clear(c: Context) {
        dir(c).listFiles()?.forEach { it.delete() }
        songs.clear()
        save(c)
    }
}
