package de.hippokratius.myfeed.core.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedLinkFinderTest {

    @Test
    fun `finds rss and atom link tags with titles`() {
        val html = """
            <html><head>
            <link rel="alternate" type="application/rss+xml" title="Alle News" href="https://example.org/rss.xml">
            <link rel="alternate" type="application/atom+xml" title="Atom" href="https://example.org/atom.xml">
            </head><body></body></html>
        """.trimIndent()

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(
            listOf(
                DiscoveredFeed("https://example.org/rss.xml", "Alle News"),
                DiscoveredFeed("https://example.org/atom.xml", "Atom"),
            ),
            feeds,
        )
    }

    @Test
    fun `resolves relative and root-relative hrefs against page url`() {
        val html = """
            <link rel="alternate" type="application/rss+xml" href="feed.xml">
            <link rel="alternate" type="application/rss+xml" href="/rss">
        """.trimIndent()

        val feeds = FeedLinkFinder.find(html, "https://www.zdf.de/nachrichten/index.html")

        assertEquals(
            listOf("https://www.zdf.de/nachrichten/feed.xml", "https://www.zdf.de/rss"),
            feeds.map { it.url },
        )
    }

    @Test
    fun `resolves relative href against host-only page url`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="feed.xml">"""

        val feeds = FeedLinkFinder.find(html, "https://example.org")

        assertEquals(listOf("https://example.org/feed.xml"), feeds.map { it.url })
    }

    @Test
    fun `base tag overrides page url for resolution`() {
        val html = """
            <base href="https://cdn.example.org/pages/">
            <link rel="alternate" type="application/rss+xml" href="feed.xml">
        """.trimIndent()

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf("https://cdn.example.org/pages/feed.xml"), feeds.map { it.url })
    }

    @Test
    fun `resolves protocol-relative href`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="//feeds.example.org/all.rss">"""

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf("https://feeds.example.org/all.rss"), feeds.map { it.url })
    }

    @Test
    fun `handles uppercase tags and attributes and single quotes`() {
        val html = """<LINK REL='Alternate' TYPE='APPLICATION/RSS+XML' HREF='/f'>"""

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf("https://example.org/f"), feeds.map { it.url })
    }

    @Test
    fun `accepts type with charset parameter and multi-token rel`() {
        val html = """<link rel="alternate feed" type="application/rss+xml; charset=UTF-8" href="/feed">"""

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf("https://example.org/feed"), feeds.map { it.url })
    }

    @Test
    fun `ignores stylesheet icon and text-html alternate links`() {
        val html = """
            <link rel="stylesheet" href="/style.css">
            <link rel="icon" href="/favicon.ico">
            <link rel="alternate" hreflang="en" type="text/html" href="/en/">
            <link rel="alternate" type="application/json" href="/feed.json">
        """.trimIndent()

        assertTrue(FeedLinkFinder.find(html, "https://example.org/").isEmpty())
    }

    @Test
    fun `decodes ampersand entity in href`() {
        val html = """<link rel="alternate" type="application/rss+xml" href="/rss.php?feed=RSS2.0&amp;lang=de">"""

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf("https://example.org/rss.php?feed=RSS2.0&lang=de"), feeds.map { it.url })
    }

    @Test
    fun `dedupes candidates with equal canonical form`() {
        val html = """
            <link rel="alternate" type="application/rss+xml" href="http://example.org/feed/">
            <link rel="alternate" type="application/rss+xml" href="https://www.example.org/feed">
        """.trimIndent()

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf("http://example.org/feed/"), feeds.map { it.url })
    }

    @Test
    fun `caps the number of candidates`() {
        val html = (1..15).joinToString("\n") {
            """<link rel="alternate" type="application/rss+xml" href="/feed$it">"""
        }

        assertEquals(10, FeedLinkFinder.find(html, "https://example.org/").size)
    }

    @Test
    fun `skips unresolvable hrefs and returns empty for plain html`() {
        val broken = """<link rel="alternate" type="application/rss+xml" href="ht tp://kaputt">"""
        assertTrue(FeedLinkFinder.find(broken, "https://example.org/").isEmpty())

        assertTrue(FeedLinkFinder.find("<html><body><p>Hallo</p></body></html>", "https://example.org/").isEmpty())
    }

    @Test
    fun `blank title becomes null and entities are decoded`() {
        val html = """
            <link rel="alternate" type="application/rss+xml" title="  " href="/a.xml">
            <link rel="alternate" type="application/rss+xml" title="News &amp; Politik" href="/b.xml">
        """.trimIndent()

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertNull(feeds[0].title)
        assertEquals("News & Politik", feeds[1].title)
    }

    @Test
    fun `handles greater-than sign inside quoted attribute values`() {
        val html = """<link rel="alternate" type="application/rss+xml" title="Home > News" href="/feed.xml">"""

        val feeds = FeedLinkFinder.find(html, "https://example.org/")

        assertEquals(listOf(DiscoveredFeed("https://example.org/feed.xml", "Home > News")), feeds)
    }

    @Test
    fun `detects charset from meta tags`() {
        assertEquals("utf-8", FeedLinkFinder.detectCharset("""<html><head><meta charset="utf-8"></head>"""))
        assertEquals(
            "iso-8859-1",
            FeedLinkFinder.detectCharset(
                """<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">""",
            ),
        )
        // Ein charset in einem <link type="…; charset=…"> ist keine Seiten-Deklaration.
        assertNull(
            FeedLinkFinder.detectCharset(
                """<link rel="alternate" type="application/rss+xml; charset=UTF-8" href="/f">""",
            ),
        )
        assertNull(FeedLinkFinder.detectCharset("<html><head></head>"))
    }

    @Test
    fun `common feed paths derive from site root of a deep url`() {
        val paths = FeedLinkFinder.commonFeedPaths("https://www.zdf.de/nachrichten/politik?x=1")

        assertEquals(
            listOf(
                "https://www.zdf.de/feed",
                "https://www.zdf.de/feed.xml",
                "https://www.zdf.de/rss",
                "https://www.zdf.de/rss.xml",
                "https://www.zdf.de/atom.xml",
                "https://www.zdf.de/index.xml",
            ),
            paths,
        )
    }

    @Test
    fun `common feed paths keep an explicit port and reject garbage`() {
        assertEquals(
            "http://example.org:8080/feed",
            FeedLinkFinder.commonFeedPaths("http://example.org:8080/blog").first(),
        )
        assertTrue(FeedLinkFinder.commonFeedPaths("kein url ###").isEmpty())
    }
}
