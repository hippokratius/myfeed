package de.hippokratius.myfeed.nextcloud

import com.nextcloud.android.sso.QueryParam
import com.nextcloud.android.sso.aidl.NextcloudRequest
import de.hippokratius.myfeed.core.nextcloud.NewsApi
import de.hippokratius.myfeed.core.nextcloud.NewsFeedsResponse
import de.hippokratius.myfeed.core.nextcloud.NewsFolder
import de.hippokratius.myfeed.core.nextcloud.NewsItem
import de.hippokratius.myfeed.core.nextcloud.NewsJson
import de.hippokratius.myfeed.core.nextcloud.NewsRoute
import de.hippokratius.myfeed.core.nextcloud.NewsRoutes
import de.hippokratius.myfeed.core.nextcloud.NewsVersion
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Führt die in `:core` gebauten [NewsRoute]s über die Files-App aus
 * (SSO-IPC) und parst die Antworten mit den `:core`-Parsern.
 */
class SsoNewsApi(private val sessionManager: SsoSessionManager) : NewsApi {

    override suspend fun version(): NewsVersion =
        execute(NewsRoutes.version()) { NewsJson.parseVersion(it) }

    override suspend fun folders(): List<NewsFolder> =
        execute(NewsRoutes.folders()) { NewsJson.parseFolders(it) }

    override suspend fun createFolder(name: String): NewsFolder? =
        execute(NewsRoutes.createFolder(name)) { NewsJson.parseFolder(it) }

    override suspend fun feeds(): NewsFeedsResponse =
        execute(NewsRoutes.feeds()) { NewsJson.parseFeeds(it) }

    override suspend fun createFeed(url: String, folderId: Long?): NewsFeedsResponse =
        execute(NewsRoutes.createFeed(url, folderId)) { NewsJson.parseFeeds(it) }

    override suspend fun deleteFeed(feedId: Long) {
        executeIgnoringBody(NewsRoutes.deleteFeed(feedId))
    }

    override suspend fun moveFeed(feedId: Long, folderId: Long?) {
        executeIgnoringBody(NewsRoutes.moveFeed(feedId, folderId))
    }

    override suspend fun items(
        type: Int,
        id: Long,
        getRead: Boolean,
        batchSize: Long,
        offset: Long,
    ): List<NewsItem> =
        execute(NewsRoutes.items(type, id, getRead, batchSize, offset)) { NewsJson.parseItems(it) }

    override suspend fun updatedItems(lastModified: Long, type: Int): List<NewsItem> =
        execute(NewsRoutes.updatedItems(lastModified, type)) { NewsJson.parseItems(it) }

    override suspend fun markItemsRead(itemIds: List<Long>) {
        executeIgnoringBody(NewsRoutes.markItemsRead(itemIds))
    }

    override suspend fun markItemsUnread(itemIds: List<Long>) {
        executeIgnoringBody(NewsRoutes.markItemsUnread(itemIds))
    }

    override suspend fun starItems(itemIds: List<Long>) {
        executeIgnoringBody(NewsRoutes.starItems(itemIds))
    }

    override suspend fun unstarItems(itemIds: List<Long>) {
        executeIgnoringBody(NewsRoutes.unstarItems(itemIds))
    }

    private suspend fun <T> execute(route: NewsRoute, parse: (InputStream) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                sessionManager.requireApi()
                    .performNetworkRequestV2(buildRequest(route))
                    .body.use { stream -> parse(stream) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw sessionManager.mapError(e)
            }
        }

    private suspend fun executeIgnoringBody(route: NewsRoute) {
        execute(route) { stream -> stream.readBytes() }
    }

    private fun buildRequest(route: NewsRoute): NextcloudRequest {
        val builder = NextcloudRequest.Builder()
            .setMethod(route.method)
            .setUrl(route.path)
        if (route.query.isNotEmpty()) {
            builder.setParameter(route.query.map { (key, value) -> QueryParam(key, value) })
        }
        val body = route.body
        if (body != null) {
            builder.setRequestBody(body)
            builder.setHeader(mapOf("Content-Type" to listOf("application/json")))
        }
        return builder.build()
    }
}
