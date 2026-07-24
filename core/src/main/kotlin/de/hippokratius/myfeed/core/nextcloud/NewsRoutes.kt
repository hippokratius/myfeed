package de.hippokratius.myfeed.core.nextcloud

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Ein API-Aufruf als reine Daten: Methode, Pfad, Query, optionaler JSON-Body. */
data class NewsRoute(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    /** JSON-Body als String (Content-Type application/json), null = kein Body. */
    val body: String? = null,
)

/**
 * Baut die Routen der News-API v1.3. Reine Funktionen ohne Netz-/Android-Bezug,
 * damit Pfade, Parameter und Bodies JVM-testbar sind.
 */
object NewsRoutes {

    const val BASE = "/index.php/apps/news/api/v1-3"

    /** Item-Typen des items-Endpunkts. */
    const val TYPE_FEED = 0
    const val TYPE_FOLDER = 1
    const val TYPE_STARRED = 2
    const val TYPE_ALL = 3

    fun version() = NewsRoute("GET", "$BASE/version")

    fun folders() = NewsRoute("GET", "$BASE/folders")

    fun createFolder(name: String) = NewsRoute(
        method = "POST",
        path = "$BASE/folders",
        body = buildJsonObject { put("name", name) }.toString(),
    )

    fun feeds() = NewsRoute("GET", "$BASE/feeds")

    fun createFeed(url: String, folderId: Long?) = NewsRoute(
        method = "POST",
        path = "$BASE/feeds",
        body = buildJsonObject {
            put("url", url)
            put("folderId", folderId)
        }.toString(),
    )

    fun deleteFeed(feedId: Long) = NewsRoute("DELETE", "$BASE/feeds/$feedId")

    fun moveFeed(feedId: Long, folderId: Long?) = NewsRoute(
        method = "POST",
        path = "$BASE/feeds/$feedId/move",
        body = buildJsonObject {
            put("folderId", folderId)
        }.toString(),
    )

    fun items(
        type: Int,
        id: Long = 0,
        getRead: Boolean,
        batchSize: Long = -1,
        offset: Long = 0,
    ) = NewsRoute(
        method = "GET",
        path = "$BASE/items",
        query = mapOf(
            "type" to type.toString(),
            "id" to id.toString(),
            "getRead" to getRead.toString(),
            "batchSize" to batchSize.toString(),
            "offset" to offset.toString(),
        ),
    )

    fun updatedItems(lastModified: Long, type: Int = TYPE_ALL, id: Long = 0) = NewsRoute(
        method = "GET",
        path = "$BASE/items/updated",
        query = mapOf(
            "lastModified" to lastModified.toString(),
            "type" to type.toString(),
            "id" to id.toString(),
        ),
    )

    fun markItemsRead(itemIds: List<Long>) = itemsAction("read", itemIds)

    fun markItemsUnread(itemIds: List<Long>) = itemsAction("unread", itemIds)

    fun starItems(itemIds: List<Long>) = itemsAction("star", itemIds)

    fun unstarItems(itemIds: List<Long>) = itemsAction("unstar", itemIds)

    private fun itemsAction(action: String, itemIds: List<Long>) = NewsRoute(
        method = "POST",
        path = "$BASE/items/$action/multiple",
        body = buildJsonObject {
            put("itemIds", buildJsonArray { itemIds.forEach { add(JsonPrimitive(it)) } })
        }.toString(),
    )
}
