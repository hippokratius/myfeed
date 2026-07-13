package de.hippokratius.kvaesitsorss.core.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FeedUrlsTest {

    @Test
    fun `http and https are equal`() {
        assertEquals(
            FeedUrls.canonical("https://example.org/feed.xml"),
            FeedUrls.canonical("http://example.org/feed.xml"),
        )
    }

    @Test
    fun `www prefix and host case are ignored`() {
        assertEquals(
            FeedUrls.canonical("https://www.Example.ORG/feed.xml"),
            FeedUrls.canonical("https://example.org/feed.xml"),
        )
    }

    @Test
    fun `trailing slash is ignored`() {
        assertEquals(
            FeedUrls.canonical("https://www.tagesschau.de/xml/rss2/"),
            FeedUrls.canonical("https://www.tagesschau.de/xml/rss2"),
        )
    }

    @Test
    fun `path case is preserved`() {
        assertNotEquals(
            FeedUrls.canonical("https://example.org/Feed.xml"),
            FeedUrls.canonical("https://example.org/feed.xml"),
        )
    }

    @Test
    fun `query parameters are preserved`() {
        assertNotEquals(
            FeedUrls.canonical("https://rss.golem.de/rss.php?feed=RSS2.0"),
            FeedUrls.canonical("https://rss.golem.de/rss.php?feed=ATOM1.0"),
        )
        // Trailing-Slash vor der Query zählt nicht.
        assertEquals(
            FeedUrls.canonical("https://example.org/rss/?feed=a"),
            FeedUrls.canonical("https://example.org/rss?feed=a"),
        )
    }

    @Test
    fun `host only url and whitespace`() {
        assertEquals("example.org", FeedUrls.canonical("  https://www.example.org/  "))
        assertEquals("example.org", FeedUrls.canonical("example.org"))
    }
}
