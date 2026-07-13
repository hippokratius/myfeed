package de.hippokratius.kvaesitsorss.fetch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Lädt Artikel-Bilder herunter, skaliert sie auf Widget-taugliche Größe
 * und legt sie als JPEG im internen Speicher ab.
 */
class ThumbnailStore(context: Context, private val client: OkHttpClient) {

    private val dir = File(context.filesDir, "thumbs")

    fun fileFor(articleId: Long): File = File(dir, "$articleId.jpg")

    /** @return Pfad zur gespeicherten Datei oder null bei Fehlern. */
    fun download(articleId: Long, url: String): String? {
        return runCatching {
            dir.mkdirs()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", FeedSyncer.USER_AGENT)
                .build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) return null
                body.byteStream().readNBytesCompat(MAX_DOWNLOAD_BYTES)
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            // Zählpixel und Spacer (z. B. 1×1-GIFs aus Feed-Beschreibungen) verwerfen.
            if (bounds.outWidth < MIN_SOURCE_DIMENSION_PX || bounds.outHeight < MIN_SOURCE_DIMENSION_PX) return null

            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION_PX ||
                bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION_PX
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

            val file = fileFor(articleId)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    fun iconFileFor(feedId: Long): File = File(dir, "feed-$feedId.png")

    /** Lädt das Feed-Logo und legt es auf 64 px skaliert als PNG ab. */
    fun downloadIcon(feedId: Long, url: String): String? {
        return runCatching {
            dir.mkdirs()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", FeedSyncer.USER_AGENT)
                .build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_DOWNLOAD_BYTES) return null
                body.byteStream().readNBytesCompat(MAX_DOWNLOAD_BYTES)
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= ICON_DIMENSION_PX ||
                bounds.outHeight / (sampleSize * 2) >= ICON_DIMENSION_PX
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

            val file = iconFileFor(feedId)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    /** Entfernt Thumbnails/Icons, deren Artikel bzw. Feeds nicht mehr existieren. */
    fun prune(validArticleIds: Set<Long>, validFeedIds: Set<Long>) {
        dir.listFiles()?.forEach { file ->
            val name = file.nameWithoutExtension
            val valid = if (name.startsWith("feed-")) {
                name.removePrefix("feed-").toLongOrNull()?.let { it in validFeedIds } == true
            } else {
                name.toLongOrNull()?.let { it in validArticleIds } == true
            }
            if (!valid) file.delete()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) return buffer.toByteArray()
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
        private const val MAX_DIMENSION_PX = 400
        private const val MIN_SOURCE_DIMENSION_PX = 48
        private const val ICON_DIMENSION_PX = 64
        private const val JPEG_QUALITY = 85
    }
}
