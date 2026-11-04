package com.shinemusic.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = LruCache<String, Int>(64)

/**
 * Pulls a rich accent colour out of album art. The colour is normalised
 * (saturation / lightness clamped) so it never turns muddy or neon, and it
 * animates smoothly when the song changes. [fallback] is used until loaded.
 */
@Composable
fun rememberArtColor(url: String?, fallback: Color): Color {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(url?.let { cache.get(it) }?.let { Color(it) } ?: fallback) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        cache.get(url)?.let { target = Color(it); return@LaunchedEffect }
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(160).build()
        val bmp = (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val picked = withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val sw = p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(sw?.rgb ?: fallback.toArgb(), hsl)
            hsl[1] = hsl[1].coerceIn(0.35f, 0.80f)
            hsl[2] = hsl[2].coerceIn(0.30f, 0.50f)
            ColorUtils.HSLToColor(hsl)
        }
        cache.put(url, picked)
        target = Color(picked)
    }
    return animateColorAsState(target, tween(800), label = "artColor").value
}
