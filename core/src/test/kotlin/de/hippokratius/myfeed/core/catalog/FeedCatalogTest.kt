package de.hippokratius.myfeed.core.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedCatalogTest {

    @Test
    fun `urls are canonically unique`() {
        val canonical = FeedCatalog.feeds.map { FeedUrls.canonical(it.url) }
        assertEquals(canonical.size, canonical.toSet().size)
    }

    @Test
    fun `all urls are https`() {
        FeedCatalog.feeds.forEach { feed ->
            assertTrue(feed.url.startsWith("https://"), "Nicht https: ${feed.url}")
        }
    }

    @Test
    fun `titles are non blank and unique`() {
        FeedCatalog.feeds.forEach { assertTrue(it.title.isNotBlank()) }
        val titles = FeedCatalog.feeds.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `every category has at least one feed`() {
        val byCategory = FeedCatalog.byCategory()
        CatalogCategory.entries.forEach { category ->
            assertTrue(!byCategory[category].isNullOrEmpty(), "Kategorie ohne Feeds: $category")
        }
    }

    @Test
    fun `languages are de or en`() {
        FeedCatalog.feeds.forEach { feed ->
            assertTrue(feed.language in setOf("de", "en"), "Unbekannte Sprache: ${feed.language}")
        }
    }
}
