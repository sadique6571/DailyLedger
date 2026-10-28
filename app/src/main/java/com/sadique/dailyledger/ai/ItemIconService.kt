package com.sadique.dailyledger.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sadique.dailyledger.data.LearnedItems
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fallback icons for unknown/custom items. Known categories use curated
 * bundled ItemArtwork instead. Downloaded files live in filesDir (permanent app
 * storage: they survive offline use, app restarts and app updates; only an
 * uninstall removes them, and then a restore re-downloads them).
 *
 * Size discipline: the source download is capped, the image is downscaled to
 * at most 128x128 and stored as compressed WebP, and anything still over the
 * per-file cap is skipped — each icon costs only a few KB of user data.
 * A failed or offline download never blocks a save: the caller keeps the
 * default icon and retries later.
 */
object ItemIconService {
    private const val DIR = "item_icons"
    const val MAX_DIMENSION = 128
    const val MAX_SOURCE_BYTES = 512 * 1024
    const val MAX_FILE_BYTES = 64 * 1024
    private const val USER_AGENT = "DailyLedger/1.0"

    fun iconDir(context: Context): File = File(context.filesDir, DIR)

    fun iconFile(context: Context, label: String): File =
        File(iconDir(context), LearnedItems.iconFileName(label))

    fun hasIcon(context: Context, label: String): Boolean =
        iconFile(context, label).let { it.exists() && it.length() > 0L }

    /** Downscale target preserving aspect ratio; never upscales. Pure. */
    fun targetSize(width: Int, height: Int, max: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        if (width <= max && height <= max) return width to height
        val scale = minOf(max.toFloat() / width, max.toFloat() / height)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /**
     * Make sure [label] has a stored icon. Returns the source URL when a NEW
     * icon was downloaded (so the caller can record it for restore), or null
     * when the icon already existed or the download failed.
     */
    suspend fun ensureIcon(context: Context, label: String, preferredUrl: String?): String? =
        withContext(Dispatchers.IO) {
            if (hasIcon(context, label)) return@withContext null
            val url = (preferredUrl?.takeIf { LearnedItems.isValidIconUrl(it) }
                ?: searchImageUrl(label)) ?: return@withContext null
            if (downloadAndStore(context, label, url)) url else null
        }

    /** Keyless public image sources, in order. No account, no key, no permission. */
    private fun searchImageUrl(query: String): String? {
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return null
        // Openverse: CC-licensed images, anonymous access.
        runCatching {
            val conn = (URL("https://api.openverse.org/v1/images/?q=$encoded&page_size=1") as HttpURLConnection).apply {
                requestMethod = "GET"; setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = false
                connectTimeout = 10_000; readTimeout = 15_000
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val results = org.json.JSONObject(body).optJSONArray("results")
                val found = if (results != null && results.length() > 0) results.getJSONObject(0).optString("url") else ""
                if (LearnedItems.isValidIconUrl(found)) return found
            }
            conn.disconnect()
        }
        // Wikimedia Commons fallback: search namespace 6 (files), 256px thumb.
        runCatching {
            val api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
                "&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=1&prop=imageinfo&iiprop=url&iiurlwidth=256"
            val conn = (URL(api) as HttpURLConnection).apply {
                requestMethod = "GET"; setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = false
                connectTimeout = 10_000; readTimeout = 15_000
            }
            if (conn.responseCode == 200) {
                val pages = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                    .optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val info = pages.getJSONObject(keys.next()).optJSONArray("imageinfo")
                    if (info != null && info.length() > 0) {
                        val thumb = info.getJSONObject(0).optString("thumburl")
                        if (LearnedItems.isValidIconUrl(thumb)) return thumb
                        val direct = info.getJSONObject(0).optString("url")
                        if (LearnedItems.isValidIconUrl(direct)) return direct
                    }
                }
            }
            conn.disconnect()
        }
        return null
    }

    /**
     * Open an image URL, following at most 3 redirects — each hop must be a
     * valid https URL (no downgrade to http). Automatic redirect following
     * is off because the final URL is not reliably observable afterwards.
     */
    private fun openImageConnection(startUrl: String): HttpURLConnection? {
        var current = startUrl
        repeat(4) {
            val conn = (URL(current) as HttpURLConnection).apply {
                requestMethod = "GET"; setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = false
                connectTimeout = 10_000; readTimeout = 15_000
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank() || !LearnedItems.isValidIconUrl(location)) return null
                current = location
            } else {
                return conn
            }
        }
        return null
    }

    private fun downloadAndStore(context: Context, label: String, url: String): Boolean {
        val conn = openImageConnection(url) ?: return false
        try {
            if (conn.responseCode != 200) return false
            if (!(conn.contentType ?: "").startsWith("image/")) return false
            if (conn.contentLength > MAX_SOURCE_BYTES) return false
            val bytes = conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SOURCE_BYTES) return false
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return false
            val (targetW, targetH) = targetSize(decoded.width, decoded.height, MAX_DIMENSION)
            val scaled = if (targetW == decoded.width && targetH == decoded.height) decoded
                else Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            try {
                val stored = compressUnderCap(scaled) ?: return false
                val dir = iconDir(context).apply { if (!exists()) mkdirs() }
                val tmp = File(dir, LearnedItems.iconFileName(label) + ".tmp")
                tmp.writeBytes(stored)
                val dest = File(dir, LearnedItems.iconFileName(label))
                if (!tmp.renameTo(dest)) {
                    dest.writeBytes(stored)
                    tmp.delete()
                }
                return true
            } finally {
                if (scaled !== decoded) decoded.recycle()
            }
        } catch (_: Exception) {
            return false
        } finally {
            conn.disconnect()
        }
    }

    /** WebP at quality 80, then 50; null when still over the per-file cap. */
    @Suppress("DEPRECATION")
    private fun compressUnderCap(bitmap: Bitmap): ByteArray? {
        for (quality in listOf(80, 50)) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) return bytes
        }
        return null
    }
}
