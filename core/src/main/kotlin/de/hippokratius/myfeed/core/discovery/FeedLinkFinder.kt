package de.hippokratius.myfeed.core.discovery

import de.hippokratius.myfeed.core.catalog.FeedUrls
import de.hippokratius.myfeed.core.rss.HtmlText
import java.net.URI

/** Ein auf einer HTML-Seite gefundener Feed-Kandidat. */
data class DiscoveredFeed(
    val url: String,
    val title: String? = null,
)

/**
 * Findet Feed-Links in HTML-Seiten: <link rel="alternate" type="application/rss+xml" …>
 * (bzw. Atom/RDF). Grundlage für die Feed-Suche per Website-URL im "Feed hinzufügen"-Dialog.
 */
object FeedLinkFinder {

    // Tags enden am ersten ">" außerhalb von Anführungszeichen (title="Home > News").
    private val LINK_TAG_REGEX = Regex("<link\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val BASE_TAG_REGEX = Regex("<base\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val META_TAG_REGEX = Regex("<meta\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", RegexOption.IGNORE_CASE)
    private val CHARSET_REGEX = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

    /** Attribut-Paare in beliebiger Reihenfolge, mit ", ' oder ohne Anführungszeichen. */
    private val ATTRIBUTE_REGEX = Regex("([a-zA-Z-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+)")

    private val WHITESPACE_REGEX = Regex("\\s+")

    private val FEED_TYPES = setOf(
        "application/rss+xml",
        "application/atom+xml",
        "application/rdf+xml",
    )

    /** Generische XML-Typen, mit denen manche Seiten ihre Feeds deklarieren. */
    private val GENERIC_XML_TYPES = setOf(
        "application/xml",
        "text/xml",
    )

    /** Gängige Feed-Pfade als Fallback für Seiten ohne <link>-Deklaration. */
    private val COMMON_PATHS = listOf(
        "/feed", "/feed.xml", "/rss", "/rss.xml", "/atom.xml", "/index.xml",
        "/feeds/posts/default", // Blogger
        "/?feed=rss2", // WordPress ohne Permalink-Struktur
    )

    private const val MAX_CANDIDATES = 10

    /**
     * Extrahiert Feed-Kandidaten aus [html]. Relative hrefs werden gegen [pageUrl]
     * aufgelöst (ein <base href> hat Vorrang). Kanonisch gleiche URLs werden nur
     * einmal geliefert, die Dokumentreihenfolge bleibt erhalten.
     */
    fun find(html: String, pageUrl: String): List<DiscoveredFeed> {
        val base = effectiveBase(html, pageUrl)
        val result = mutableListOf<DiscoveredFeed>()
        val seen = mutableSetOf<String>()

        for (tag in LINK_TAG_REGEX.findAll(html)) {
            if (result.size >= MAX_CANDIDATES) break
            val attributes = parseAttributes(tag.value)
            if (!isFeedLink(attributes)) continue
            val href = attributes["href"]?.replace("&amp;", "&")?.trim()
            if (href.isNullOrEmpty()) continue
            val url = resolve(base, href) ?: continue
            if (!seen.add(FeedUrls.canonical(url))) continue
            result += DiscoveredFeed(url = url, title = HtmlText.clean(attributes["title"]).ifBlank { null })
        }
        return result
    }

    /**
     * Liest den in <meta charset=…> bzw. <meta http-equiv content="…charset=…"> deklarierten
     * Zeichensatz aus dem Seitenanfang (für Server, die keinen im Content-Type mitschicken).
     */
    fun detectCharset(htmlPrefix: String): String? {
        for (tag in META_TAG_REGEX.findAll(htmlPrefix)) {
            val charset = CHARSET_REGEX.find(tag.value) ?: continue
            return charset.groupValues[1]
        }
        return null
    }

    /** Typische Feed-Pfade an der Site-Root von [pageUrl] (Fallback ohne <link>-Tags). */
    fun commonFeedPaths(pageUrl: String): List<String> {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return emptyList()
        val host = uri.host ?: return emptyList()
        val scheme = uri.scheme ?: "https"
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return COMMON_PATHS.map { "$scheme://$host$port$it" }
    }

    private fun isFeedLink(attributes: Map<String, String>): Boolean {
        val relTokens = attributes["rel"].orEmpty().lowercase().split(WHITESPACE_REGEX)
        if ("alternate" !in relTokens && "feed" !in relTokens) return false
        val type = attributes["type"].orEmpty().substringBefore(';').trim().lowercase()
        if (type in FEED_TYPES || type in GENERIC_XML_TYPES) return true
        // rel="feed" (WHATWG) darf ohne type auskommen; falsche Kandidaten scheitern
        // später ohnehin an der Verifikation durch echtes Laden und Parsen.
        return "feed" in relTokens && type.isEmpty()
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
        if (resolved.scheme?.lowercase() in FEED_URL_SCHEMES) resolved.toString() else null
    }.getOrNull()

    private val FEED_URL_SCHEMES = setOf("http", "https")
}
