package de.hippokratius.kvaesitsorss.core.rss

import de.hippokratius.kvaesitsorss.core.model.ParsedFeed
import de.hippokratius.kvaesitsorss.core.model.RssItem
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parser für RSS 2.0, RSS 1.0 (RDF) und Atom auf Basis des in JVM und Android
 * gleichermaßen verfügbaren DOM-APIs. Bewusst tolerant: fehlende Felder führen
 * nicht zum Abbruch, nur Einträge ohne Titel und Link werden verworfen.
 */
object RssXmlParser {

    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

    class FeedFormatException(message: String) : Exception(message)

    fun parse(input: InputStream): ParsedFeed {
        val document = newDocumentBuilder().parse(input)
        val root = document.documentElement
            ?: throw FeedFormatException("Leeres XML-Dokument")
        return when (root.localOrTagName().lowercase()) {
            "rss" -> parseRss(root)
            "feed" -> parseAtom(root)
            "rdf" -> parseRdf(root)
            else -> throw FeedFormatException("Unbekanntes Feed-Format: <${root.tagName}>")
        }
    }

    fun parse(xml: String): ParsedFeed = parse(xml.byteInputStream(Charsets.UTF_8))

    private fun newDocumentBuilder() =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // XXE-Härtung: keine externen Entities auflösen.
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }.newDocumentBuilder().apply {
            setErrorHandler(null)
        }

    // ---- RSS 2.0 ----

    private fun parseRss(root: Element): ParsedFeed {
        val channel = root.firstChild("channel")
            ?: throw FeedFormatException("RSS ohne <channel>")
        val feedTitle = HtmlText.clean(channel.firstChild("title")?.text()).ifBlank { null }
        val items = channel.children("item").mapNotNull { parseRssItem(it) }
        return ParsedFeed(feedTitle, items, channelIconUrl(channel))
    }

    /** RSS: channel > image > url; itunes:image als Fallback. */
    private fun channelIconUrl(channel: Element): String? {
        val imageUrl = channel.firstChild("image")?.firstChild("url")?.text()?.trim()
        if (imageUrl.isHttpUrl()) return imageUrl
        val itunesHref = channel.children("image")
            .firstOrNull { it.getAttribute("href").isHttpUrl() }
            ?.getAttribute("href")?.trim()
        return itunesHref?.takeIf { it.isHttpUrl() }
    }

    private fun parseRssItem(item: Element): RssItem? {
        val title = HtmlText.clean(item.firstChild("title")?.text())
        val guidElement = item.firstChild("guid")
        var link = item.firstChild("link")?.text()?.trim().orEmpty()
        if (!link.isHttpUrl()) {
            val guidText = guidElement?.text()?.trim().orEmpty()
            if (guidText.isHttpUrl()) link = guidText
        }
        if (title.isBlank() || !link.isHttpUrl()) return null

        val guid = guidElement?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: link
        val published = FeedDates.parseToEpochMillis(
            item.firstChild("pubDate")?.text() ?: item.firstChild("date")?.text(),
        )
        val descriptionHtml = item.firstChild("description")?.text()
            ?: item.firstChild("encoded")?.text()
        val image = findEnclosureImage(item)
            ?: findMediaImage(item)
            ?: HtmlText.firstImageSrc(descriptionHtml)

        return RssItem(title, link, guid, image, published)
    }

    // ---- Atom ----

    private fun parseAtom(root: Element): ParsedFeed {
        val feedTitle = HtmlText.clean(root.firstChild("title")?.text()).ifBlank { null }
        val items = root.children("entry").mapNotNull { parseAtomEntry(it) }
        val icon = (root.firstChild("icon")?.text() ?: root.firstChild("logo")?.text())
            ?.trim()?.takeIf { it.isHttpUrl() }
        return ParsedFeed(feedTitle, items, icon)
    }

    private fun parseAtomEntry(entry: Element): RssItem? {
        val title = HtmlText.clean(entry.firstChild("title")?.text())
        val links = entry.children("link")
        val link = (
            links.firstOrNull { it.getAttribute("rel") == "alternate" }
                ?: links.firstOrNull { it.getAttribute("rel").isNullOrEmpty() }
                ?: links.firstOrNull()
            )?.getAttribute("href")?.trim().orEmpty()
        if (title.isBlank() || !link.isHttpUrl()) return null

        val guid = entry.firstChild("id")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: link
        val published = FeedDates.parseToEpochMillis(
            entry.firstChild("published")?.text() ?: entry.firstChild("updated")?.text(),
        )
        val contentHtml = entry.firstChild("content")?.text()
            ?: entry.firstChild("summary")?.text()
        val enclosureImage = links.firstOrNull {
            it.getAttribute("rel") == "enclosure" && looksLikeImage(it.getAttribute("type"), it.getAttribute("href"))
        }?.getAttribute("href")
        val image = enclosureImage
            ?: findMediaImage(entry)
            ?: HtmlText.firstImageSrc(contentHtml)

        return RssItem(title, link, guid, image, published)
    }

    // ---- RSS 1.0 / RDF ----

    private fun parseRdf(root: Element): ParsedFeed {
        val channel = root.firstChild("channel")
        val feedTitle = HtmlText.clean(channel?.firstChild("title")?.text()).ifBlank { null }
        val items = root.children("item").mapNotNull { parseRssItem(it) }
        // RSS 1.0: <image> liegt als Geschwister des <channel> und enthält <url>.
        val icon = root.firstChild("image")?.firstChild("url")?.text()?.trim()
            ?.takeIf { it.isHttpUrl() }
        return ParsedFeed(feedTitle, items, icon)
    }

    // ---- Bilder ----

    private fun findEnclosureImage(item: Element): String? =
        item.children("enclosure").firstOrNull {
            looksLikeImage(it.getAttribute("type"), it.getAttribute("url"))
        }?.getAttribute("url")?.takeIf { it.isHttpUrl() }

    /** Sucht media:content / media:thumbnail / itunes:image, auch in media:group. */
    private fun findMediaImage(item: Element): String? {
        val candidates = mutableListOf<Pair<String, Int>>()
        collectMediaImages(item, candidates, depth = 0)
        return candidates.maxByOrNull { it.second }?.first
    }

    private fun collectMediaImages(element: Element, out: MutableList<Pair<String, Int>>, depth: Int) {
        if (depth > 3) return
        for (child in element.childElements()) {
            when (child.localOrTagName().lowercase()) {
                "thumbnail" -> {
                    val url = child.getAttribute("url")
                    if (url.isHttpUrl()) out += url to (child.getAttribute("width").toIntOrNullSafe() ?: 1)
                }
                "content" -> {
                    val url = child.getAttribute("url")
                    val medium = child.getAttribute("medium")
                    val type = child.getAttribute("type")
                    if (url.isHttpUrl() && (medium == "image" || looksLikeImage(type, url))) {
                        out += url to (child.getAttribute("width").toIntOrNullSafe() ?: 2)
                    }
                    collectMediaImages(child, out, depth + 1)
                }
                "image" -> {
                    val href = child.getAttribute("href")
                    if (href.isHttpUrl()) out += href to 1
                }
                "group" -> collectMediaImages(child, out, depth + 1)
            }
        }
    }

    private fun looksLikeImage(mimeType: String?, url: String?): Boolean {
        if (mimeType?.startsWith("image/") == true) return true
        val path = url?.substringBefore('?')?.lowercase() ?: return false
        return IMAGE_EXTENSIONS.any { path.endsWith(it) }
    }

    // ---- DOM-Helfer ----

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element) result += node
            node = node.nextSibling
        }
        return result
    }

    private fun Element.children(localName: String): List<Element> =
        childElements().filter { it.localOrTagName().equals(localName, ignoreCase = true) }

    private fun Element.firstChild(localName: String): Element? =
        childElements().firstOrNull { it.localOrTagName().equals(localName, ignoreCase = true) }

    private fun Element.text(): String = textContent.orEmpty()

    private fun Element.localOrTagName(): String =
        localName ?: tagName.substringAfter(':')

    private fun String?.isHttpUrl(): Boolean =
        this != null && (startsWith("http://") || startsWith("https://"))

    private fun String?.toIntOrNullSafe(): Int? = this?.trim()?.toIntOrNull()
}
