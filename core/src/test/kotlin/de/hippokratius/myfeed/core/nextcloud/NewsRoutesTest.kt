package de.hippokratius.myfeed.core.nextcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewsRoutesTest {

    @Test
    fun `items route carries all query params`() {
        val route = NewsRoutes.items(type = NewsRoutes.TYPE_ALL, getRead = false)

        assertEquals("GET", route.method)
        assertEquals("/index.php/apps/news/api/v1-3/items", route.path)
        assertEquals("3", route.query["type"])
        assertEquals("false", route.query["getRead"])
        assertEquals("-1", route.query["batchSize"])
        assertEquals("0", route.query["offset"])
        assertNull(route.body)
    }

    @Test
    fun `updated items route uses lastModified cursor`() {
        val route = NewsRoutes.updatedItems(lastModified = 1367273003)

        assertEquals("/index.php/apps/news/api/v1-3/items/updated", route.path)
        assertEquals("1367273003", route.query["lastModified"])
        assertEquals("3", route.query["type"])
    }

    @Test
    fun `status push routes build itemIds json body`() {
        val route = NewsRoutes.markItemsRead(listOf(2, 3))

        assertEquals("POST", route.method)
        assertEquals("/index.php/apps/news/api/v1-3/items/read/multiple", route.path)
        assertEquals("""{"itemIds":[2,3]}""", route.body)

        assertEquals(
            "/index.php/apps/news/api/v1-3/items/unstar/multiple",
            NewsRoutes.unstarItems(listOf(1)).path,
        )
    }

    @Test
    fun `feed creation encodes null folder as json null`() {
        assertEquals(
            """{"url":"https://example.org/feed","folderId":null}""",
            NewsRoutes.createFeed("https://example.org/feed", null).body,
        )
        assertEquals(
            """{"url":"https://example.org/feed","folderId":7}""",
            NewsRoutes.createFeed("https://example.org/feed", 7).body,
        )
        assertEquals("""{"folderId":3}""", NewsRoutes.moveFeed(9, 3).body)
        assertEquals("DELETE", NewsRoutes.deleteFeed(9).method)
    }
}
