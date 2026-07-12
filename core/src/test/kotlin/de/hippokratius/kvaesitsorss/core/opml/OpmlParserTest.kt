package de.hippokratius.kvaesitsorss.core.opml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpmlParserTest {

    @Test
    fun `parses flat and nested outlines and dedupes`() {
        val feeds = OpmlParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Meine Feeds</title></head>
              <body>
                <outline text="Tagesschau" title="Tagesschau" type="rss"
                         xmlUrl="https://www.tagesschau.de/xml/rss2/" htmlUrl="https://www.tagesschau.de"/>
                <outline text="Technik">
                  <outline text="heise online" type="rss" xmlUrl="https://www.heise.de/rss/heise-atom.xml"/>
                  <outline text="Doppelt" type="rss" xmlUrl="https://www.tagesschau.de/xml/rss2/"/>
                </outline>
                <outline text="Kein Feed, nur Ordner"/>
              </body>
            </opml>
            """.trimIndent(),
        )

        assertEquals(2, feeds.size)
        assertEquals("Tagesschau", feeds[0].title)
        assertEquals("https://www.tagesschau.de/xml/rss2/", feeds[0].xmlUrl)
        assertEquals("heise online", feeds[1].title)
    }

    @Test
    fun `uses text attribute when title is missing`() {
        val feeds = OpmlParser.parse(
            """
            <opml version="1.0"><body>
              <outline text="Nur Text" xmlUrl="https://example.org/feed.xml"/>
            </body></opml>
            """.trimIndent(),
        )
        assertEquals("Nur Text", feeds.single().title)
    }

    @Test
    fun `rejects non opml xml`() {
        assertFailsWith<OpmlParser.OpmlFormatException> {
            OpmlParser.parse("<rss version=\"2.0\"><channel/></rss>")
        }
    }
}
