package de.hippokratius.myfeed.core.opml

import de.hippokratius.myfeed.core.model.OpmlFeed
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Liest Feed-URLs aus einer OPML-Datei. Verschachtelte <outline>-Ordner werden
 * rekursiv durchlaufen und ihre Namen als Kategorie übernommen (innerster
 * Ordner gewinnt); Duplikate (gleiche xmlUrl) werden entfernt.
 */
object OpmlParser {

    class OpmlFormatException(message: String) : Exception(message)

    fun parse(input: InputStream): List<OpmlFeed> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().apply { setErrorHandler(null) }.parse(input)
        val root = document.documentElement ?: throw OpmlFormatException("Leere OPML-Datei")
        if (!root.tagName.equals("opml", ignoreCase = true)) {
            throw OpmlFormatException("Keine OPML-Datei: <${root.tagName}>")
        }

        val feeds = mutableListOf<OpmlFeed>()
        collectOutlines(root, feeds, category = null)
        return feeds.distinctBy { it.xmlUrl }
    }

    fun parse(xml: String): List<OpmlFeed> = parse(xml.byteInputStream(Charsets.UTF_8))

    private fun collectOutlines(element: Element, out: MutableList<OpmlFeed>, category: String?) {
        var node: Node? = element.firstChild
        while (node != null) {
            if (node is Element) {
                var childCategory = category
                if (node.tagName.equals("outline", ignoreCase = true)) {
                    val xmlUrl = node.getAttribute("xmlUrl").trim()
                    val label = node.getAttribute("title").ifBlank { node.getAttribute("text") }
                        .trim().ifBlank { null }
                    if (xmlUrl.startsWith("http://") || xmlUrl.startsWith("https://")) {
                        out += OpmlFeed(label, xmlUrl, category)
                    } else if (label != null) {
                        // Outline ohne Feed-URL = Ordner: Name wird Kategorie der Kinder.
                        childCategory = label
                    }
                }
                collectOutlines(node, out, childCategory)
            }
            node = node.nextSibling
        }
    }
}
