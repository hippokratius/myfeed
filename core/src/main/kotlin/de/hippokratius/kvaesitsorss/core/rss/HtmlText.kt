package de.hippokratius.kvaesitsorss.core.rss

/** Kleine Helfer, um HTML-Fragmente aus Feeds in reinen Text zu verwandeln. */
object HtmlText {

    private val TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val NUMERIC_ENTITY_REGEX = Regex("&#(x?)([0-9a-fA-F]+);")
    private val IMG_SRC_REGEX = Regex(
        "<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE,
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

    /** Liefert die erste Bild-URL aus einem HTML-Fragment (z. B. description). */
    fun firstImageSrc(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val src = IMG_SRC_REGEX.find(html)?.groupValues?.get(1)?.trim()
        return src?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
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
