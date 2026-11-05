package com.shinemusic.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the cover art for the media notification, lock screen and system media card.
 *
 * Reliability measures (a missing cover shows the grey music-note on the lock screen):
 *  1. last covers are kept in memory, so the lock screen gets the bitmap instantly (no async race)
 *  2. Coil first (same cache / redirects / https as the in-app covers), then a plain HTTP download
 *  3. one automatic retry after a short pause (flaky mobile data)
 *  4. bitmaps are copied to our own ARGB_8888 and limited to 512 px (safe for the system card)
 *  5. [prefetch] warms the cache for the current + next song
 * Failures are logged under the tag "ShineCover".
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(context: Context) : BitmapLoader {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, Bitmap>(10)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = future {
        decode(data) ?: throw IllegalStateException("Could not decode artwork")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()
        cache.get(key)?.let { if (!it.isRecycled) return Futures.immediateFuture(it) }
        return future { fetch(key) }
    }

    /** Fire-and-forget: have the cover ready before the system asks for it. */
    fun prefetch(uri: Uri?) {
        val key = uri?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (cache.get(key) != null) return
        scope.launch { runCatching { fetch(key) } }
    }

    // ------------------------------------------------------------------ internals

    private fun future(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val f = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try { f.set(block()) } catch (e: Throwable) { f.setException(e) }
        }
        f.addListener({ if (f.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return f
    }

    private suspend fun fetch(url: String): Bitmap {
        repeat(2) { attempt ->
            val bmp = viaCoil(url) ?: withContext(Dispatchers.IO) { download(url) }
            if (bmp != null) {
                cache.put(url, bmp)
                return bmp
            }
            if (attempt == 0) delay(1200)
        }
        Log.w(TAG, "No cover could be loaded for $url")
        throw IllegalStateException("No artwork for $url")
    }

    private suspend fun viaCoil(url: String): Bitmap? = try {
        val req = ImageRequest.Builder(app)
            .data(url)
            .allowHardware(false)          // notification / system card needs a software bitmap
            .size(512, 512)
            .build()
        val res = Coil.imageLoader(app).execute(req)
        val src = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        src?.let { limit(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "Coil failed for $url: ${e.message}")
        null
    }

    /** Plain HTTP fallback with redirects and a browser-like User-Agent. */
    private fun download(url: String): Bitmap? {
        var target = url
        repeat(4) {
            val c = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ShineMusic")
                setRequestProperty("Accept", "image/*")
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return null
                    target = URL(URL(target), loc).toString()
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP $code for $target")
                    return null
                }
                return decode(c.inputStream.use { it.readBytes() })
            } catch (e: Exception) {
                Log.w(TAG, "Download failed for $target: ${e.message}")
                return null
            } finally {
                c.disconnect()
            }
        }
        return null
    }

    private fun decode(data: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)?.let { limit(it) }
    }.getOrNull()

    /** Own ARGB_8888 copy, longest side at most 512 px. */
    private fun limit(src: Bitmap): Bitmap {
        val max = 512
        val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        return scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
    }

    private companion object { const val TAG = "ShineCover" }
}
