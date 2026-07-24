package de.hippokratius.myfeed.core.nextcloud

import de.hippokratius.myfeed.core.rss.HtmlText

/** Mapping-Regeln News-Item → MyFeed-Artikel (reine Funktionen, JVM-testbar). */
object NewsItemMapper {

    /**
     * Bild-URL mit Prioritätskette: mediaThumbnail → Bild-Enclosure → erstes
     * echtes Bild aus dem HTML-Body (Tracker-Pixel filtert [HtmlText] heraus).
     */
    fun imageUrl(item: NewsItem): String? {
        item.mediaThumbnail?.takeIf { it.startsWith("http") }?.let { return it }
        if (item.enclosureMime?.startsWith("image/") == true) {
            item.enclosureLink?.takeIf { it.startsWith("http") }?.let { return it }
        }
        return HtmlText.firstImageSrc(item.body)
    }

    /** pubDate (Sekunden) → Epoch-Millis; fehlend → Sync-Zeitpunkt. */
    fun publishedAtMillis(item: NewsItem, nowMillis: Long): Long =
        item.pubDate?.takeIf { it > 0 }?.times(1000) ?: nowMillis

    /** Stabile GUID mit Fallback-Kette guid → url → "nc-{id}". */
    fun guid(item: NewsItem): String =
        item.guid?.takeIf { it.isNotBlank() }
            ?: item.url?.takeIf { it.isNotBlank() }
            ?: "nc-${item.id}"

    /** Titel-Fallback: HTML-bereinigt, notfalls die URL. */
    fun title(item: NewsItem): String =
        HtmlText.clean(item.title).ifBlank { item.url ?: "" }
}
