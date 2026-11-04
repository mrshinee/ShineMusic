package com.shinemusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.shinemusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

/**
 * In-app updates from GitHub Releases.
 * Publish a release whose tag is higher than the installed versionName, attach the signed APK and
 * write the release notes in the description - the app shows them in the "Update Available" dialog.
 */
object UpdateChecker {

    val enabled: Boolean
        get() = BuildConfig.UPDATE_REPO.contains('/') && !BuildConfig.UPDATE_REPO.startsWith("YOUR_")

    private fun http(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("User-Agent", "ShineMusic")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    /** The newest published release if it is newer than this install, else null. Never throws. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        runCatching {
            val c = http("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=5")
            val body = try {
                if (c.responseCode != 200) return@runCatching null
                c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }

            val arr = org.json.JSONArray(body)
            // newest non-draft release (pre-releases / alpha builds included)
            val rel = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") }
                ?: return@runCatching null
            val tag = rel.optString("tag_name")
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null

            val assets = rel.optJSONArray("assets") ?: return@runCatching null
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            // several APKs (per-ABI builds)? prefer the one matching this phone
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val apk = apks.firstOrNull { abi.isNotEmpty() && it.optString("name").contains(abi, true) }
                ?: apks.firstOrNull() ?: return@runCatching null

            UpdateInfo(
                version = tag,
                notes = rel.optString("body"),
                apkUrl = apk.optString("browser_download_url"),
                sizeBytes = apk.optLong("size", 0L)
            )
        }.getOrNull()
    }

    /** "v1.2.0" > "1.1" : compares the numbers in each version, left to right. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = Regex("\\d+").findAll(remote).map { it.value.toLong() }.toList()
        val l = Regex("\\d+").findAll(local).map { it.value.toLong() }.toList()
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0L }
            val b = l.getOrElse(i) { 0L }
            if (a != b) return a > b
        }
        return false
    }

    /** Downloads the APK into the app cache and reports progress 0f..1f. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "ShineMusic-update.apk")
            val tmp = File(dir, "download.part")
            val c = http(info.apkUrl)
            try {
                c.setRequestProperty("Accept", "application/octet-stream")
                if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                c.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) { last = pct; onProgress(done.toFloat() / total) }
                            }
                        }
                    }
                }
            } finally { c.disconnect() }
            if (!tmp.renameTo(out)) throw java.io.IOException("Could not save update")
            out
        }

    /** Opens the system installer. Returns false if the user first has to allow "install unknown apps". */
    fun install(context: Context, file: File): Boolean {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
