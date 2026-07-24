package de.hippokratius.myfeed.backend

import de.hippokratius.myfeed.core.model.OpmlFeed
import de.hippokratius.myfeed.data.ArticleDao
import de.hippokratius.myfeed.data.FeedDao
import de.hippokratius.myfeed.data.FeedEntity
import de.hippokratius.myfeed.data.Origin
import de.hippokratius.myfeed.fetch.FeedSyncer
import de.hippokratius.myfeed.fetch.SyncPostProcessor

/**
 * Das bisherige Verhalten hinter der Backend-Naht: Feeds direkt per HTTP laden,
 * alle Zustände rein lokal. Reiner Umzug der früheren Direktaufrufe aus den
 * Screens – keine Verhaltensänderung.
 */
class LocalRssBackend(
    private val feedSyncer: FeedSyncer,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val postProcessor: SyncPostProcessor,
) : FeedBackend {

    override val mode = BackendMode.LOCAL_RSS

    override val capabilities = BackendCapabilities(feedManagementNeedsNetwork = false)

    override suspend fun syncAll() {
        postProcessor.withSyncLock {
            feedSyncer.fetchAllLocalFeeds()
            postProcessor.run(Origin.LOCAL)
        }
    }

    override suspend fun addFeed(url: String, title: String?, category: String?) {
        feedDao.insert(
            FeedEntity(url = url, title = title ?: "", category = category, origin = Origin.LOCAL),
        )
    }

    override suspend fun deleteFeed(feed: FeedEntity) {
        feedDao.delete(feed)
        postProcessor.regroupAndRefreshWidget()
    }

    override suspend fun setCategory(feed: FeedEntity, category: String?) {
        feedDao.updateCategory(feed.id, category)
        postProcessor.regroupAndRefreshWidget()
    }

    override suspend fun importOpml(feeds: List<OpmlFeed>): ImportResult {
        var added = 0
        for (feed in feeds) {
            val id = feedDao.insert(
                FeedEntity(
                    url = feed.xmlUrl,
                    title = feed.title ?: "",
                    category = feed.category,
                    origin = Origin.LOCAL,
                ),
            )
            if (id != -1L) added++
        }
        return ImportResult(added = added, failed = 0)
    }

    override suspend fun markRead(articleIds: List<Long>, readAt: Long) {
        articleDao.markRead(articleIds, readAt)
    }

    override suspend fun markOpened(articleId: Long, timestamp: Long) {
        articleDao.markArchived(articleId, timestamp)
    }

    override suspend fun setBookmarked(articleId: Long, bookmarkedAt: Long?) {
        articleDao.setBookmarked(articleId, bookmarkedAt)
    }
}
