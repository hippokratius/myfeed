package de.hippokratius.myfeed.core.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RssXmlParserTest {

    @Test
    fun `parses rss 2_0 with enclosure image and rfc1123 date`() {
        val feed = RssXmlParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Tagesschau</title>
                <image>
                  <url>https://example.org/logo.png</url>
                  <title>Tagesschau</title>
                  <link>https://example.org</link>
                </image>
                <item>
                  <title><![CDATA[Bundesregierung beschließt neues Klimapaket]]></title>
                  <link>https://example.org/politik/klimapaket</link>
                  <guid isPermaLink="false">tag:example.org,2026:1234</guid>
                  <pubDate>Fri, 10 Jul 2026 08:30:00 +0200</pubDate>
                  <enclosure url="https://example.org/img/klima.jpg" type="image/jpeg" length="12345"/>
                  <description>Die Bundesregierung hat sich geeinigt.</description>
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )

        assertEquals("Tagesschau", feed.title)
        assertEquals("https://example.org/logo.png", feed.iconUrl)
        assertEquals(1, feed.items.size)
        val item = feed.items.single()
        assertEquals("Bundesregierung beschließt neues Klimapaket", item.title)
        assertEquals("https://example.org/politik/klimapaket", item.link)
        assertEquals("tag:example.org,2026:1234", item.guid)
        assertEquals("https://example.org/img/klima.jpg", item.imageUrl)
        assertNotNull(item.publishedAtMillis)
    }

    @Test
    fun `parses media content image and falls back to description img`() {
        val feed = RssXmlParser.parse(
            """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Heise</title>
                <item>
                  <title>Neuer Chip vorgestellt &amp; getestet</title>
                  <link>https://example.org/chip</link>
                  <media:content url="https://example.org/img/chip-small.jpg" medium="image" width="200"/>
                  <media:content url="https://example.org/img/chip-big.jpg" medium="image" width="800"/>
                </item>
                <item>
                  <title>Ohne Media-Tag</title>
                  <link>https://example.org/fallback</link>
                  <description>&lt;p&gt;Text &lt;img src="https://example.org/img/inline.png" alt=""&gt;&lt;/p&gt;</description>
                </item>
                <item>
                  <title>Ganz ohne Bild</title>
                  <link>https://example.org/nobild</link>
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )

        assertEquals("Neuer Chip vorgestellt & getestet", feed.items[0].title)
        assertEquals("https://example.org/img/chip-big.jpg", feed.items[0].imageUrl)
        assertEquals("https://example.org/img/inline.png", feed.items[1].imageUrl)
        assertNull(feed.items[2].imageUrl)
    }

    @Test
    fun `feed without declared logo has null iconUrl`() {
        val feed = RssXmlParser.parse(
            """
            <rss version="2.0"><channel><title>Ohne Logo</title>
              <item><title>A</title><link>https://example.org/a</link></item>
            </channel></rss>
            """.trimIndent(),
        )
        assertNull(feed.iconUrl)
    }

    @Test
    fun `item without parseable date gets null timestamp`() {
        val feed = RssXmlParser.parse(
            """
            <rss version="2.0"><channel><title>T</title>
              <item>
                <title>Titel</title>
                <link>https://example.org/a</link>
                <pubDate>irgendwann demnächst</pubDate>
              </item>
            </channel></rss>
            """.trimIndent(),
        )
        assertNull(feed.items.single().publishedAtMillis)
    }

    @Test
    fun `items without title or link are dropped`() {
        val feed = RssXmlParser.parse(
            """
            <rss version="2.0"><channel><title>T</title>
              <item><title>Nur Titel, kein Link</title></item>
              <item><link>https://example.org/nur-link</link></item>
              <item><title>Gültig</title><link>https://example.org/ok</link></item>
            </channel></rss>
            """.trimIndent(),
        )
        assertEquals(listOf("Gültig"), feed.items.map { it.title })
    }

    @Test
    fun `uses guid as link when link element is missing but guid is a url`() {
        val feed = RssXmlParser.parse(
            """
            <rss version="2.0"><channel><title>T</title>
              <item>
                <title>Nur Guid</title>
                <guid isPermaLink="true">https://example.org/guid-link</guid>
              </item>
            </channel></rss>
            """.trimIndent(),
        )
        assertEquals("https://example.org/guid-link", feed.items.single().link)
    }

    @Test
    fun `parses atom feed`() {
        val feed = RssXmlParser.parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
              <title>Beispiel-Blog</title>
              <icon>https://example.org/favicon.png</icon>
              <entry>
                <title>Hello World</title>
                <link rel="alternate" type="text/html" href="https://example.org/hello"/>
                <link rel="enclosure" type="image/png" href="https://example.org/hello.png"/>
                <id>urn:uuid:1</id>
                <published>2026-07-10T06:00:00Z</published>
                <summary>Erster Beitrag</summary>
              </entry>
              <entry>
                <title>Zweiter Beitrag</title>
                <link href="https://example.org/second"/>
                <id>urn:uuid:2</id>
                <updated>2026-07-11T06:00:00+02:00</updated>
                <media:thumbnail url="https://example.org/second-thumb.jpg"/>
              </entry>
            </feed>
            """.trimIndent(),
        )

        assertEquals("Beispiel-Blog", feed.title)
        assertEquals("https://example.org/favicon.png", feed.iconUrl)
        assertEquals(2, feed.items.size)
        assertEquals("https://example.org/hello", feed.items[0].link)
        assertEquals("https://example.org/hello.png", feed.items[0].imageUrl)
        assertEquals("urn:uuid:1", feed.items[0].guid)
        assertNotNull(feed.items[0].publishedAtMillis)
        assertEquals("https://example.org/second-thumb.jpg", feed.items[1].imageUrl)
        assertNotNull(feed.items[1].publishedAtMillis)
    }

    @Test
    fun `parses rss 1_0 rdf feed`() {
        val feed = RssXmlParser.parse(
            """
            <?xml version="1.0"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/"
                     xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://example.org/">
                <title>RDF-Feed</title>
              </channel>
              <item rdf:about="https://example.org/rdf-item">
                <title>RDF-Artikel</title>
                <link>https://example.org/rdf-item</link>
                <dc:date>2026-07-09T12:00:00Z</dc:date>
              </item>
            </rdf:RDF>
            """.trimIndent(),
        )

        assertEquals("RDF-Feed", feed.title)
        val item = feed.items.single()
        assertEquals("RDF-Artikel", item.title)
        assertNotNull(item.publishedAtMillis)
    }

    @Test
    fun `rejects non-feed xml`() {
        assertFailsWith<RssXmlParser.FeedFormatException> {
            RssXmlParser.parse("<html><body>Keine News</body></html>")
        }
    }
}
