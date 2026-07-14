package de.hippokratius.myfeed.core.rss

/** Kleine Helfer, um HTML-Fragmente aus Feeds in reinen Text zu verwandeln. */
object HtmlText {

    private val TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(x?)([0-9a-fA-F]+);")
    private val IMG_TAG_REGEX = Regex("<img[^>]*>", RegexOption.IGNORE_CASE)
    private val SRC_ATTR_REGEX = Regex(
        "src\\s*=\\s*[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val WIDTH_ATTR_REGEX = Regex("width\\s*=\\s*[\"']?(\\d+)", RegexOption.IGNORE_CASE)
    private val HEIGHT_ATTR_REGEX = Regex("height\\s*=\\s*[\"']?(\\d+)", RegexOption.IGNORE_CASE)

    /**
     * Zählpixel und Werbe-Tracker, die Feeds als <img> mitliefern
     * (Golem cpx.php, VG-Wort-Pixel, Ad-Server), aber keine Artikelbilder sind.
     */
    private val TRACKER_URL_MARKERS = listOf(
        "cpx.golem.de", "/cpx.php", "vgwort.de", "doubleclick.net",
        "feedburner.com/~ff/", "feedsportal.com", "smartadserver.com",
    )

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "shy" to "", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü",
        "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß",
        "eacute" to "é", "egrave" to "è", "agrave" to "à", "ccedil" to "ç",
        "euro" to "€", "deg" to "°", "sect" to "§", "copy" to "©", "reg" to "®",
    )

    /** Entfernt Tags, dekodiert Entities und normalisiert Whitespace. */
    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.replace(TAG_REGEX, " ")
        text = decodeEntities(text)
        // Doppelt kodierte Feeds ("&amp;amp;") noch einmal dekodieren.
        if (text.contains("&") && text.contains(";")) {
            text = decodeEntities(text)
        }
        return text.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Liefert die erste echte Bild-URL aus einem HTML-Fragment (z. B. description).
     * Zählpixel (bekannte Tracker-URLs oder deklarierte Größe ≤ 2 px) werden übersprungen.
     */
    fun firstImageSrc(html: String?): String? {
        if (html.isNullOrBlank()) return null
        for (tag in IMG_TAG_REGEX.findAll(html)) {
            val src = SRC_ATTR_REGEX.find(tag.value)?.groupValues?.get(1)?.trim() ?: continue
            if (!src.startsWith("http://") && !src.startsWith("https://")) continue
            if (isTrackingPixel(tag.value, src)) continue
            return src
        }
        return null
    }

    private fun isTrackingPixel(imgTag: String, src: String): Boolean {
        val srcLower = src.lowercase()
        if (TRACKER_URL_MARKERS.any { it in srcLower }) return true
        val width = WIDTH_ATTR_REGEX.find(imgTag)?.groupValues?.get(1)?.toIntOrNull()
        val height = HEIGHT_ATTR_REGEX.find(imgTag)?.groupValues?.get(1)?.toIntOrNull()
        return (width != null && width <= 2) || (height != null && height <= 2)
    }

    private fun decodeEntities(input: String): String {
        var text = NUMERIC_ENTITY_REGEX.replace(input) { match ->
            val isHex = match.groupValues[1].isNotEmpty()
            val code = match.groupValues[2].toIntOrNull(if (isHex) 16 else 10)
            if (code != null && code > 0 && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }
        for ((name, replacement) in NAMED_ENTITIES) {
            text = text.replace("&$name;", replacement)
        }
        return text
    }
}
