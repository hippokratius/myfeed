package de.hippokratius.kvaesitsorss.core.model

/** Ein einzelner Eintrag aus einem RSS-/Atom-Feed. */
data class RssItem(
    val title: String,
    val link: String,
    val guid: String,
    val imageUrl: String? = null,
    /** Veröffentlichungszeitpunkt in Epoch-Millis, null wenn nicht parsebar. */
    val publishedAtMillis: Long? = null,
)

/** Ergebnis des Parsens eines Feeds. */
data class ParsedFeed(
    val title: String?,
    val items: List<RssItem>,
)

/** Ein Feed-Eintrag aus einer OPML-Datei. */
data class OpmlFeed(
    val title: String?,
    val xmlUrl: String,
)
