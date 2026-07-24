package de.hippokratius.myfeed.core.nextcloud

/**
 * Aufruf-Oberfläche der News-API. Die Android-Implementierung führt die Routen
 * über die Nextcloud-Files-App aus (SSO); Tests verwenden einen Fake.
 */
interface NewsApi {
    suspend fun version(): NewsVersion
    suspend fun folders(): List<NewsFolder>
    suspend fun createFolder(name: String): NewsFolder?
    suspend fun feeds(): NewsFeedsResponse
    suspend fun createFeed(url: String, folderId: Long?): NewsFeedsResponse
    suspend fun deleteFeed(feedId: Long)
    suspend fun moveFeed(feedId: Long, folderId: Long?)
    suspend fun items(
        type: Int,
        id: Long = 0,
        getRead: Boolean,
        batchSize: Long = -1,
        offset: Long = 0,
    ): List<NewsItem>
    suspend fun updatedItems(lastModified: Long, type: Int = NewsRoutes.TYPE_ALL): List<NewsItem>
    suspend fun markItemsRead(itemIds: List<Long>)
    suspend fun markItemsUnread(itemIds: List<Long>)
    suspend fun starItems(itemIds: List<Long>)
    suspend fun unstarItems(itemIds: List<Long>)
}
