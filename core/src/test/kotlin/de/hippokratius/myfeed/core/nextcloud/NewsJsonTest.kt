package de.hippokratius.myfeed.core.nextcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewsJsonTest {

    private fun stream(json: String) = json.byteInputStream()

    @Test
    fun `parses items with all fields`() {
        val items = NewsJson.parseItems(
            stream(
                """
                {"items":[{
                    "id": 3443,
                    "guid": "http://grulja.wordpress.com/?p=76",
                    "guidHash": "3059047a572cd9cd5d0bf645faffd077",
                    "url": "http://grulja.wordpress.com/2013/04/29/plasma-nm-after-the-solid-sprint/",
                    "title": "Plasma-nm after the solid sprint",
                    "author": "Jan Grulich (grulja)",
                    "pubDate": 1367270544,
                    "body": "<p>At first I have to say...</p>",
                    "enclosureMime": null,
                    "enclosureLink": null,
                    "mediaThumbnail": "https://example.org/thumb.jpg",
                    "feedId": 67,
                    "unread": true,
                    "starred": false,
                    "rtl": false,
                    "lastModified": 1367273003,
                    "fingerprint": "aeaae2123"
                }]}
                """.trimIndent(),
            ),
        )

        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(3443L, item.id)
        assertEquals(67L, item.feedId)
        assertEquals("http://grulja.wordpress.com/?p=76", item.guid)
        assertEquals(1367270544L, item.pubDate)
        assertEquals(1367273003L, item.lastModified)
        assertEquals("https://example.org/thumb.jpg", item.mediaThumbnail)
        assertTrue(item.unread)
        assertFalse(item.starred)
    }

    @Test
    fun `tolerates missing optionals, unknown keys and quoted numbers`() {
        val items = NewsJson.parseItems(
            stream(
                """
                {"items":[{
                    "id": 1, "feedId": 2,
                    "lastModified": "1367273003",
                    "zukunftsFeld": {"nested": true}
                }]}
                """.trimIndent(),
            ),
        )

        assertEquals(1367273003L, items[0].lastModified)
        assertNull(items[0].guid)
        assertNull(items[0].pubDate)
        assertFalse(items[0].unread)
    }

    @Test
    fun `parses feeds with null and missing folderId`() {
        val response = NewsJson.parseFeeds(
            stream(
                """
                {"starredCount": 2, "newestItemId": 3443, "feeds": [
                    {"id": 27, "url": "http://example.org/feed.rss", "title": "Beispiel",
                     "faviconLink": "http://example.org/favicon.ico", "folderId": null},
                    {"id": 28, "url": "http://example.com/feed.rss", "folderId": 5}
                ]}
                """.trimIndent(),
            ),
        )

        assertEquals(2, response.feeds.size)
        assertNull(response.feeds[0].folderId)
        assertEquals(5L, response.feeds[1].folderId)
        assertNull(response.feeds[1].title)
        assertEquals(3443L, response.newestItemId)
    }

    @Test
    fun `parses folders, version and empty lists`() {
        val folders = NewsJson.parseFolders(stream("""{"folders":[{"id":3,"name":"Technik"}]}"""))
        assertEquals(listOf(NewsFolder(3, "Technik")), folders)

        assertEquals("21.2.0", NewsJson.parseVersion(stream("""{"version":"21.2.0"}""")).version)
        assertTrue(NewsJson.parseItems(stream("""{"items":[]}""")).isEmpty())
        assertTrue(NewsJson.parseFolders(stream("""{"folders":[]}""")).isEmpty())
    }
}
