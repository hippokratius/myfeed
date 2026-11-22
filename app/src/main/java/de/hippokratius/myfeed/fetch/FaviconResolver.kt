package de.hippokratius.myfeed.fetch

import de.hippokratius.myfeed.core.discovery.FaviconLinkFinder
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Ergebnis der Favicon-Auflösung: Quelle-URL und gespeicherter Dateipfad. */
data class ResolvedIcon(val url: String, val path: String)

/**
 * Favicon-Fallback für Feeds ohne deklariertes Logo. Wenn das deklarierte Icon
 * fehlt oder nicht ladbar ist, probiert [resolve] der Reihe nach:
 *  1. `<link rel="icon"|"apple-touch-icon">` auf der Startseite,
 *  2. PWA-Manifest (`/manifest.json`) – z. B. DER STANDARD,
 *  3. `/favicon.ico` an der Site-Root.
 *
 * Für jeden Kandidaten wird [ThumbnailStore.downloadIcon] aufgerufen; gewonnen
 * hat die erste URL, die sich wirklich zu einem Bild dekodieren lässt. Ein reiner
 * Content-Type-Check genügt nicht – heise liefert z. B. ein ICO unter
 * /favicon.ico, das BitmapFactory nicht lesen kann; die Startseite verweist
 * dagegen auf dekodierbare PNGs.
 */
class FaviconResolver(
    private val client: OkHttpClient,
    private val thumbnailStore: ThumbnailStore,
) {

    /** Kurze Gesamtdauer: Favicons sind Beiwerk, kein Sync-Kern. */
    private val discoveryClient: OkHttpClient = client.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Liefert für [feedId]/[feedUrl] das erste dekodierbare Icon – das
     * deklarierte [declared] zuerst, sonst die Fallback-Kette – oder null.
     */
    suspend fun resolve(feedId: Long, feedUrl: String, declared: String?): ResolvedIcon? =
        withContext(Dispatchers.IO) {
            val origin = FaviconLinkFinder.origin(feedUrl)
            val steps: List<suspend () -> String?> = buildList {
                if (declared != null) add { declared }
                if (origin != null) {
                    add {
                        fetchText(origin, MAX_HTML_BYTES)
                            ?.let { FaviconLinkFinder.iconFromHtml(it, origin) }
                    }
                    val manifestUrl = FaviconLinkFinder.manifestUrl(feedUrl)
                    if (manifestUrl != null) {
                        add {
                            fetchText(manifestUrl, MAX_MANIFEST_BYTES)
                                ?.let { FaviconLinkFinder.iconFromManifest(it, manifestUrl) }
                        }
                    }
                    add { FaviconLinkFinder.originFaviconUrl(feedUrl) }
                }
            }
            val tried = mutableSetOf<String>()
            for (step in steps) {
                val url = runCatching { step() }.getOrNull()
                if (url.isNullOrBlank() || !tried.add(url)) continue
                val path = thumbnailStore.downloadIcon(feedId, url)
                if (path != null) return@withContext ResolvedIcon(url, path)
            }
            null
        }

    private fun fetchText(url: String, maxBytes: Int): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", FeedSyncer.BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .build()
        discoveryClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val bytes = body.byteStream().readBounded(maxBytes)
            decode(bytes, response.header("Content-Type"))
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val charsetName = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (charsetName != null) {
            runCatching { return String(bytes, Charset.forName(charsetName)) }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < maxBytes) {
            val read = read(chunk, 0, minOf(chunk.size, maxBytes - total))
            if (read < 0) break
            buffer.write(chunk, 0, read)
            total += read
        }
        return buffer.toByteArray()
    }

    companion object {
        private const val MAX_HTML_BYTES = 256 * 1024
        private const val MAX_MANIFEST_BYTES = 512 * 1024
    }
}
