package com.vui.vaporwave.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ARGB per artwork URI; NO_ACCENT marks art with no usable colour (e.g. greyscale), so it isn't
// re-decoded every time that track comes round again.
private const val NO_ACCENT = 0
private val accentCache = LruCache<String, Int>(64)

/**
 * The dominant vivid colour of [artworkUri]'s cover, or null while it's loading, when there's no
 * artwork, or when the artwork has no usable colour. Holds the previous track's colour until the
 * new one is ready, so changing track fades between two colours instead of flashing the fallback.
 */
@Composable
fun rememberArtworkAccent(artworkUri: Uri?, enabled: Boolean): Color? {
    val context = LocalContext.current
    var accent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(artworkUri, enabled) {
        accent = if (!enabled || artworkUri == null) null else loadArtworkAccent(context, artworkUri)
    }
    return accent
}

private suspend fun loadArtworkAccent(context: Context, uri: Uri): Color? {
    val key = uri.toString()
    accentCache.get(key)?.let { return if (it == NO_ACCENT) null else Color(it) }
    val color = withContext(Dispatchers.Default) {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(Size(64, 64))
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            val bitmap = (result as? SuccessResult)?.image?.toBitmap()
            bitmap?.let { dominantVividColor(it) }
        } catch (e: Exception) {
            null
        }
    }
    accentCache.put(key, color?.let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) } ?: NO_ACCENT)
    return color
}

/**
 * Buckets every reasonably colourful pixel by hue, weighting each by how saturated and bright it
 * is, and takes the average colour of the heaviest bucket -- then nudges it into a range that
 * still reads clearly on both light and dark backgrounds. Washed-out, near-black and near-white
 * pixels are ignored; null when nothing colourful is left.
 */
private fun dominantVividColor(bitmap: Bitmap): Color? {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val buckets = 24
    val weight = FloatArray(buckets)
    val sumR = FloatArray(buckets)
    val sumG = FloatArray(buckets)
    val sumB = FloatArray(buckets)
    val hsv = FloatArray(3)
    for (pixel in pixels) {
        if (android.graphics.Color.alpha(pixel) < 128) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        val value = hsv[2]
        if (saturation < 0.25f || value < 0.2f || value > 0.97f) continue
        val w = saturation * value
        val bucket = (hsv[0] / (360f / buckets)).toInt().coerceIn(0, buckets - 1)
        weight[bucket] += w
        sumR[bucket] += android.graphics.Color.red(pixel) * w
        sumG[bucket] += android.graphics.Color.green(pixel) * w
        sumB[bucket] += android.graphics.Color.blue(pixel) * w
    }
    var best = 0
    for (i in 1 until buckets) if (weight[i] > weight[best]) best = i
    // Too little colour in the whole picture to call any of it "the" colour.
    if (weight[best] < 4f) return null

    val r = (sumR[best] / weight[best]).toInt().coerceIn(0, 255)
    val g = (sumG[best] / weight[best]).toInt().coerceIn(0, 255)
    val b = (sumB[best] / weight[best]).toInt().coerceIn(0, 255)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.5f)
    // Lifted a little above the artwork's own colour so it reads clearly against the dark sheet.
    hsv[2] = (hsv[2] * 1.08f + 0.02f).coerceIn(0.72f, 0.98f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
