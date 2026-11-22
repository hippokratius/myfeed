package de.hippokratius.myfeed.core.discovery

import java.net.URI

/**
 * Findet Favicon-URLs für Feeds, die kein Logo deklarieren (RSS ohne <image>,
 * Atom ohne <icon>/<logo>). Zwei Quellen:
 *  - `<link rel="icon"|"shortcut icon"|"apple-touch-icon">` auf der Startseite,
 *  - das `icons`-Array eines PWA-Manifests (`/manifest.json`).
 *
 * Bewusst konservativ: geliefert werden nur Rasterformate, die BitmapFactory
 * dekodieren kann – SVG, ICO (trotz der Endung) und unbekannte Formate werden
 * übersprungen, weil der Download sonst garantiert fehlschlägt.
 */
object FaviconLinkFinder {

    // Tags enden am ersten ">" außerhalb von Anführungszeichen (title="Home > News").
    private val LINK_TAG_REGEX = Regex("<link\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val BASE_TAG_REGEX = Regex("<base\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE_REGEX = Regex("([a-zA-Z-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+)")
    // Icon-Objekte im Manifest sind flach; verschachtelte Objekte kommen dort nicht vor.
    private val JSON_OBJECT_REGEX = Regex("\\{[^{}]*\\}")
    private val JSON_STRING_REGEX = Regex("\"([a-zA-Z0-9_-]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    private val WHITESPACE_REGEX = Regex("\\s+")

    /** Rasterformate, die BitmapFactory dekodieren kann. */
    private val RASTER_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif")

    /** Bevorzugte Icon-Größen: apple-touch (180) und 192er-Manifest-Icons. */
    private val PREFERRED_SIZES = listOf("192", "180")

    /** "scheme://host" (optional mit Port) zur Feed-URL; null bei unbrauchbaren URLs. */
    fun origin(feedUrl: String): String? {
        val uri = runCatching { URI(feedUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    /** Klassische Root-Datei, z. B. https://www.heise.de/favicon.ico */
    fun originFaviconUrl(feedUrl: String): String? = origin(feedUrl)?.let { "$it/favicon.ico" }

    /** PWA-Manifest an der Site-Root, z. B. https://www.derstandard.at/manifest.json */
    fun manifestUrl(feedUrl: String): String? = origin(feedUrl)?.let { "$it/manifest.json" }

    /**
     * Beste `<link rel="icon">`-URL aus [html], aufgelöst gegen [pageUrl], oder null.
     * Gewählt wird das beste Rasterformat: apple-touch-icon und größere Angaben
     * (sizes 180/192) schlagen kleinere PNGs, PNG schlägt andere Rasterformate.
     */
    fun iconFromHtml(html: String, pageUrl: String): String? {
        val base = effectiveBase(html, pageUrl) ?: return null
        var best: Candidate? = null
        for (tag in LINK_TAG_REGEX.findAll(html)) {
            val attributes = parseAttributes(tag.value)
            val relTokens = attributes["rel"].orEmpty().lowercase().split(WHITESPACE_REGEX)
            if (relTokens.none { it == "icon" || it.endsWith("icon") }) continue
            val href = attributes["href"]?.replace("&amp;", "&")?.trim()
            if (href.isNullOrEmpty()) continue
            val url = resolve(base, href) ?: continue
            val candidate = Candidate(url, score(relTokens, attributes["sizes"], attributes["type"], url))
            if (best == null || candidate.score > best.score) best = candidate
        }
        return best?.url
    }

    /**
     * Beste Icon-URL aus einem PWA-Manifest ([manifest]), aufgelöst gegen
     * [manifestUrl], oder null. Ausgewertet wird nur das `icons`-Array.
     */
    fun iconFromManifest(manifest: String, manifestUrl: String): String? {
        val base = runCatching { URI(manifestUrl.trim()) }.getOrNull() ?: return null
        val iconsStart = Regex("\"icons\"\\s*:\\s*\\[", RegexOption.IGNORE_CASE).find(manifest)
            ?: return null
        val array = readJsonArray(manifest, iconsStart.range.last + 1) ?: return null
        var best: Candidate? = null
        for (objectText in JSON_OBJECT_REGEX.findAll(array)) {
            val attributes = parseJsonAttributes(objectText.value)
            val src = attributes["src"]?.trim()
            if (src.isNullOrEmpty()) continue
            val url = resolve(base, src) ?: continue
            val candidate = Candidate(url, score(emptyList(), attributes["sizes"], attributes["type"], url))
            if (best == null || candidate.score > best.score) best = candidate
        }
        return best?.url
    }

    private data class Candidate(val url: String, val score: Int)

    /** Bewertung: -1 für nicht dekodierbare Formate, sonst höher = besser. */
    private fun score(relTokens: List<String>, sizes: String?, type: String?, url: String): Int {
        val path = url.substringBefore('?').lowercase()
        val isSvg = path.endsWith(".svg") || type?.contains("svg", ignoreCase = true) == true
        if (isSvg) return -1
        val isRasterByExtension = RASTER_EXTENSIONS.any { path.endsWith(it) }
        val isRasterByType = type?.startsWith("image/") == true
        if (!isRasterByExtension && !isRasterByType) return -1
        var score = 0
        if ("apple-touch-icon" in relTokens) score += 2
        val sizeTokens = sizes.orEmpty().lowercase()
        if (PREFERRED_SIZES.any { sizeTokens.contains(it) }) score += 1
        if (path.endsWith(".png") || type.equals("image/png", ignoreCase = true)) score += 1
        return score
    }

    /** Liest den JSON-Array-Inhalt ab [start] (öffnende Klammer bereits verbraucht). */
    private fun readJsonArray(text: String, start: Int): String? {
        var depth = 1
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else when (c) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i)
                }
            }
        }
        return null
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        for (match in ATTRIBUTE_REGEX.findAll(tag)) {
            val name = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim('"', '\'')
            attributes.putIfAbsent(name, value)
        }
        return attributes
    }

    private fun parseJsonAttributes(objectText: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        for (match in JSON_STRING_REGEX.findAll(objectText)) {
            val name = match.groupValues[1].lowercase()
            val value = match.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
            attributes.putIfAbsent(name, value)
        }
        return attributes
    }

    /** Basis für relative hrefs: <base href> (gegen die Seiten-URL aufgelöst), sonst die Seiten-URL. */
    private fun effectiveBase(html: String, pageUrl: String): URI? {
        val page = baseUri(pageUrl)
        val baseHref = BASE_TAG_REGEX.find(html)
            ?.let { parseAttributes(it.value)["href"]?.trim() }
        if (baseHref.isNullOrEmpty()) return page
        val resolved = runCatching { page?.resolve(baseHref) }.getOrNull() ?: return page
        return baseUri(resolved.toString()) ?: page
    }

    /** URI mit garantiert nicht-leerem Pfad, damit URI.resolve relative Pfade korrekt anhängt. */
    private fun baseUri(url: String): URI? = runCatching {
        val uri = URI(url.trim())
        if (uri.host != null && uri.path.isNullOrEmpty()) {
            URI(uri.scheme, uri.authority, "/", uri.query, uri.fragment)
        } else {
            uri
        }
    }.getOrNull()

    private fun resolve(base: URI?, href: String): String? = runCatching {
        val resolved = base?.resolve(href) ?: URI(href)
        val scheme = resolved.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") resolved.toString() else null
    }.getOrNull()
}
