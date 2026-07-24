package de.hippokratius.myfeed.core.nextcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedReconcilerTest {

    private val folders = listOf(NewsFolder(3, "Technik"), NewsFolder(4, ""))

    @Test
    fun `new server feed becomes insert with folder as category`() {
        val changes = FeedReconciler.reconcile(
            local = emptyList(),
            serverFeeds = listOf(
                NewsFeed(id = 27, url = "https://heise.de/rss", title = "heise", folderId = 3, faviconLink = "https://heise.de/fav.ico"),
            ),
            serverFolders = folders,
        )

        assertEquals(1, changes.inserts.size)
        val insert = changes.inserts[0]
        assertEquals(27, insert.remoteId)
        assertEquals("Technik", insert.category)
        assertEquals("https://heise.de/fav.ico", insert.iconUrl)
        assertTrue(changes.updates.isEmpty())
        assertTrue(changes.deleteRemoteIds.isEmpty())
    }

    @Test
    fun `root folder, unknown folder and blank folder name map to no category`() {
        val changes = FeedReconciler.reconcile(
            local = emptyList(),
            serverFeeds = listOf(
                NewsFeed(id = 1, url = "https://a.de/rss", title = "A", folderId = null),
                NewsFeed(id = 2, url = "https://b.de/rss", title = "B", folderId = 0),
                NewsFeed(id = 3, url = "https://c.de/rss", title = "C", folderId = 99),
                NewsFeed(id = 4, url = "https://d.de/rss", title = "D", folderId = 4),
            ),
            serverFolders = folders,
        )

        assertTrue(changes.inserts.all { it.category == null })
    }

    @Test
    fun `changed title, folder move and new favicon become one update`() {
        val local = listOf(
            LocalNcFeed(27, "https://heise.de/rss", "alt", category = null, iconUrl = null),
            LocalNcFeed(28, "https://a.de/rss", "A", category = "Sport", iconUrl = "x"),
        )
        val changes = FeedReconciler.reconcile(
            local = local,
            serverFeeds = listOf(
                NewsFeed(id = 27, url = "https://heise.de/rss", title = "heise online", folderId = 3, faviconLink = "fav"),
                NewsFeed(id = 28, url = "https://a.de/rss", title = "A", folderId = 3, faviconLink = "x"),
            ),
            serverFolders = folders,
        )

        assertEquals(2, changes.updates.size)
        val heise = changes.updates.first { it.remoteId == 27L }
        assertEquals("heise online", heise.title)
        assertEquals("Technik", heise.category)
        assertEquals("fav", heise.iconUrl)
        assertTrue(changes.inserts.isEmpty())
    }

    @Test
    fun `unchanged feed produces no update and vanished feed is deleted`() {
        val local = listOf(
            LocalNcFeed(27, "https://heise.de/rss", "heise", "Technik", "fav"),
            LocalNcFeed(99, "https://weg.de/rss", "Weg", null, null),
        )
        val changes = FeedReconciler.reconcile(
            local = local,
            serverFeeds = listOf(
                NewsFeed(id = 27, url = "https://heise.de/rss", title = "heise", folderId = 3, faviconLink = "fav"),
            ),
            serverFolders = folders,
        )

        assertTrue(changes.updates.isEmpty())
        assertEquals(listOf(99L), changes.deleteRemoteIds)
    }

    @Test
    fun `title falls back to url then id and missing url gets placeholder`() {
        val changes = FeedReconciler.reconcile(
            local = emptyList(),
            serverFeeds = listOf(
                NewsFeed(id = 1, url = "https://a.de/rss", title = ""),
                NewsFeed(id = 2, url = null, title = null),
            ),
            serverFolders = emptyList(),
        )

        assertEquals("https://a.de/rss", changes.inserts[0].title)
        assertEquals("2", changes.inserts[1].title)
        assertEquals("nc-feed-2", changes.inserts[1].url)
        assertNull(changes.inserts[1].iconUrl)
    }
}
