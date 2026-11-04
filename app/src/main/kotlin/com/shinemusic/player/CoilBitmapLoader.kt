package com.shinemusic.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Loads the cover art for the media notification, lock screen and system media card.
 *
 * Media3's built-in loader failed on some CDN links, which is why the system card showed the
 * default music-note. Coil is already used for the in-app covers (same cache, redirects, https,
 * webp) so the same URL is guaranteed to work here too.
 */
@OptIn(UnstableApi::class)
class CoilBitmapLoader(context: Context) : BitmapLoader {

    private val app = context.applicationContext
    private val loader = ImageLoader(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val f = SettableFuture.create<Bitmap>()
        scope.launch {
            val bmp = runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
            if (bmp != null) f.set(bmp) else f.setException(IllegalStateException("Could not decode artwork"))
        }
        return f
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val f = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                val req = ImageRequest.Builder(app)
                    .data(uri.toString())
                    .allowHardware(false)          // notification needs a software bitmap
                    .size(512, 512)
                    .build()
                val res = loader.execute(req)
                val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null) f.set(bmp) else f.setException(IllegalStateException("No artwork for $uri"))
            } catch (e: Exception) {
                f.setException(e)
            }
        }
        return f
    }
}
