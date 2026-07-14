package de.hippokratius.myfeed.core.filter

import java.util.Locale

/**
 * Wortfilter für Artikel-Titel: Groß-/kleinschreibungsunabhängige
 * Teilstring-Suche, damit deutsche Komposita mitgetroffen werden
 * ("Fußball" trifft auch "Fußballspieler").
 */
class WordFilter(words: Collection<String>) {

    // Locale.ROOT beidseitig, damit das Ergebnis nicht von der
    // System-Locale abhängt (z. B. türkisches ı/İ).
    private val needles = words.map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .distinct()

    val isEmpty: Boolean get() = needles.isEmpty()

    fun matches(title: String): Boolean {
        if (needles.isEmpty()) return false
        val haystack = title.lowercase(Locale.ROOT)
        return needles.any { it in haystack }
    }
}
