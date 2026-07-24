package de.hippokratius.myfeed.nextcloud

import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import de.hippokratius.myfeed.backend.BackendCapabilities
import de.hippokratius.myfeed.backend.BackendException
import de.hippokratius.myfeed.backend.BackendMode
import de.hippokratius.myfeed.backend.FeedBackend
import de.hippokratius.myfeed.backend.ImportResult
import de.hippokratius.myfeed.core.model.OpmlFeed
import de.hippokratius.myfeed.core.nextcloud.FeedReconciler
import de.hippokratius.myfeed.core.nextcloud.LocalNcFeed
import de.hippokratius.myfeed.core.nextcloud.LocalStatus
import de.hippokratius.myfeed.core.nextcloud.NewsApi
import de.hippokratius.myfeed.core.nextcloud.NewsItem
import de.hippokratius.myfeed.core.nextcloud.NewsItemMapper
import de.hippokratius.myfeed.core.nextcloud.NewsRoutes
import de.hippokratius.myfeed.core.nextcloud.NewsSyncLogic
import de.hippokratius.myfeed.data.ArticleDao
import de.hippokratius.myfeed.data.ArticleEntity
import de.hippokratius.myfeed.data.FeedDao
import de.hippokratius.myfeed.data.FeedEntity
import de.hippokratius.myfeed.data.Origin
import de.hippokratius.myfeed.fetch.SyncPostProcessor
import de.hippokratius.myfeed.fetch.ThumbnailStore
import de.hippokratius.myfeed.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Nextcloud-News-Backend: spiegelt Feeds und Artikel des Servers in die lokale
 * Datenbank (origin = NEXTCLOUD) und pusht Gelesen-/Stern-Änderungen zurück.
 * Ablauf pro Sync (Konzept §4.2): Lebenszyklus (§4.3, Opt-in) → Pending-Deltas
 * pushen → Feeds/Ordner abgleichen → Items ziehen → mergen → Cursor →
 * gemeinsame Nachverarbeitung.
 */
class NextcloudNewsBackend(
    private val api: NewsApi,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val settingsRepository: SettingsRepository,
    private val thumbnailStore: ThumbnailStore,
    private val postProcessor: SyncPostProcessor,
) : FeedBackend {

    override val mode = BackendMode.NEXTCLOUD_NEWS

    override val capabilities = BackendCapabilities(feedManagementNeedsNetwork = true)

    override suspend fun syncAll() = withContext(Dispatchers.IO) {
        postProcessor.withSyncLock {
            try {
                syncLocked()
                settingsRepository.setNcLastSyncError(null)
            } catch (e: Exception) {
                settingsRepository.setNcLastSyncError(e.message ?: e.javaClass.simpleName)
                throw e
            }
        }
    }

    private suspend fun syncLocked() {
        val settings = settingsRepository.current()
        val now = System.currentTimeMillis()

        // 0. Server-Lebenszyklus (§4.3, Opt-in): abgelaufene ungelesene Artikel
        //    als gelesen vormerken, optional abgelaufene Lesezeichen entsternen.
        //    Muss VOR Push und Aufräumen laufen, damit nichts verloren geht.
        if (settings.ncManageLifecycle) {
            articleDao.expireUnread(settings.feedCutoffMillis(now), now)
            if (settings.ncManageStars) {
                articleDao.expireBookmarks(settings.bookmarkCutoffMillis(now))
            }
        }

        // 1. Lokale Deltas ZUERST pushen, damit der Pull sie nicht zurückdreht.
        pushPendingDeltas()

        // 2. Ordner + Feeds abgleichen (Server ist Quelle der Wahrheit).
        reconcileFeeds()

        // 3. Items ziehen: initial ungelesen + gesternt, danach inkrementell.
        val cursor = settings.ncLastModified
        val items = if (cursor == 0L) {
            val unread = api.items(type = NewsRoutes.TYPE_ALL, getRead = false, batchSize = -1)
            val starred = api.items(type = NewsRoutes.TYPE_STARRED, getRead = true, batchSize = -1)
            (unread + starred).distinctBy { it.id }
        } else {
            api.updatedItems(cursor)
        }

        // 4. Mergen (Upsert über remoteId, Pending-Flags gewinnen).
        mergeItems(items, now)

        // 5. Cursor fortschreiben (max lastModified, bewusst ohne "+1").
        settingsRepository.setNcLastModified(NewsSyncLogic.nextCursor(cursor, items))

        // 6. Gemeinsame Nachverarbeitung (Aufbewahrung, Thumbnails, Gruppen, Widget).
        postProcessor.run(Origin.NEXTCLOUD)
    }

    private suspend fun pushPendingDeltas() {
        val readIds = articleDao.pendingReadRemoteIds()
        for (chunk in NewsSyncLogic.chunkIds(readIds)) {
            api.markItemsRead(chunk)
            articleDao.clearPendingRead(chunk)
        }
        val starIds = articleDao.pendingStarRemoteIds()
        for (chunk in NewsSyncLogic.chunkIds(starIds)) {
            api.starItems(chunk)
            articleDao.clearPendingStar(chunk)
        }
        val unstarIds = articleDao.pendingUnstarRemoteIds()
        for (chunk in NewsSyncLogic.chunkIds(unstarIds)) {
            api.unstarItems(chunk)
            articleDao.clearPendingStar(chunk)
        }
    }

    private suspend fun reconcileFeeds() {
        val serverFolders = api.folders()
        val serverFeeds = api.feeds().feeds
        applyFeedChanges(serverFeeds, serverFolders)
    }

    private suspend fun applyFeedChanges(
        serverFeeds: List<de.hippokratius.myfeed.core.nextcloud.NewsFeed>,
        serverFolders: List<de.hippokratius.myfeed.core.nextcloud.NewsFolder>,
    ) {
        val localFeeds = feedDao.getByOrigin(Origin.NEXTCLOUD)
        val localByRemoteId = localFeeds.associateBy { it.remoteId }
        val changes = FeedReconciler.reconcile(
            local = localFeeds.mapNotNull { feed ->
                feed.remoteId?.let {
                    LocalNcFeed(it, feed.url, feed.title, feed.category, feed.iconUrl)
                }
            },
            serverFeeds = serverFeeds,
            serverFolders = serverFolders,
        )

        if (changes.deleteRemoteIds.isNotEmpty()) {
            // Serverseitig gelöscht: Feed samt Artikeln entfernen (FK-CASCADE).
            feedDao.deleteByRemoteIds(changes.deleteRemoteIds)
        }

        for (insert in changes.inserts) {
            val id = feedDao.insert(
                FeedEntity(
                    url = insert.url,
                    title = insert.title,
                    iconUrl = insert.iconUrl,
                    category = insert.category,
                    origin = Origin.NEXTCLOUD,
                    remoteId = insert.remoteId,
                ),
            )
            val insertIconUrl = insert.iconUrl
            if (id != -1L && insertIconUrl != null) {
                thumbnailStore.downloadIcon(id, insertIconUrl)?.let { path ->
                    feedDao.updateIcon(id, insertIconUrl, path)
                }
            }
        }

        for (update in changes.updates) {
            val existing = localByRemoteId[update.remoteId] ?: continue
            feedDao.updateFromServer(update.remoteId, update.title, update.category, update.iconUrl)
            if (existing.title != update.title) {
                articleDao.updateSourceTitle(existing.id, update.title)
            }
            val iconUrl = update.iconUrl
            if (iconUrl != null && (iconUrl != existing.iconUrl || existing.iconPath == null)) {
                thumbnailStore.downloadIcon(existing.id, iconUrl)?.let { path ->
                    feedDao.updateIcon(existing.id, iconUrl, path)
                }
            }
        }
    }

    private suspend fun mergeItems(items: List<NewsItem>, now: Long) {
        if (items.isEmpty()) return
        val feedsByRemoteId = feedDao.getByOrigin(Origin.NEXTCLOUD).associateBy { it.remoteId }

        for (chunk in items.chunked(MERGE_CHUNK_SIZE)) {
            val existingByRemoteId = articleDao.byRemoteIds(chunk.map { it.id })
                .associateBy { it.remoteId }
            val inserts = mutableListOf<ArticleEntity>()
            val updates = mutableListOf<ArticleEntity>()

            for (item in chunk) {
                val feed = feedsByRemoteId[item.feedId] ?: continue
                val serverStamp = item.lastModified * 1000
                val existing = existingByRemoteId[item.id]

                if (existing == null) {
                    inserts += ArticleEntity(
                        feedId = feed.id,
                        sourceTitle = feed.title,
                        guid = NewsItemMapper.guid(item),
                        title = NewsItemMapper.title(item),
                        link = item.url ?: "",
                        imageUrl = NewsItemMapper.imageUrl(item),
                        thumbPath = null,
                        publishedAt = NewsItemMapper.publishedAtMillis(item, now),
                        fetchedAt = now,
                        groupId = null,
                        readAt = if (item.unread) null else serverStamp,
                        bookmarkedAt = if (item.starred) serverStamp else null,
                        origin = Origin.NEXTCLOUD,
                        remoteId = item.id,
                    )
                } else {
                    val merged = NewsSyncLogic.mergedStatus(
                        LocalStatus(
                            readAt = existing.readAt,
                            bookmarkedAt = existing.bookmarkedAt,
                            pendingReadSync = existing.pendingReadSync,
                            pendingStarSync = existing.pendingStarSync,
                        ),
                        item,
                    )
                    // archivedAt und thumbPath bleiben bewusst unangetastet.
                    updates += existing.copy(
                        feedId = feed.id,
                        sourceTitle = feed.title,
                        title = NewsItemMapper.title(item),
                        link = item.url ?: existing.link,
                        imageUrl = NewsItemMapper.imageUrl(item) ?: existing.imageUrl,
                        publishedAt = NewsItemMapper.publishedAtMillis(item, existing.publishedAt),
                        readAt = merged.readAt,
                        bookmarkedAt = merged.bookmarkedAt,
                    )
                }
            }

            articleDao.insertAll(inserts)
            articleDao.updateAll(updates)
        }
    }

    // ---- Feed-Verwaltung (läuft gegen den Server, E7: Discovery macht die UI) ----

    override suspend fun addFeed(url: String, title: String?, category: String?) {
        val folderId = category?.let { ensureFolderId(it) }
        try {
            api.createFeed(url, folderId)
        } catch (e: BackendException.ServerUnreachable) {
            throw asFeedRejected(e) ?: e
        }
        reconcileFeeds()
    }

    override suspend fun deleteFeed(feed: FeedEntity) {
        feed.remoteId?.let { api.deleteFeed(it) }
        feedDao.delete(feed)
        postProcessor.regroupAndRefreshWidget()
    }

    override suspend fun setCategory(feed: FeedEntity, category: String?) {
        val remoteId = feed.remoteId ?: return
        val folderId = category?.let { ensureFolderId(it) }
        api.moveFeed(remoteId, folderId)
        feedDao.updateCategory(feed.id, category)
        postProcessor.regroupAndRefreshWidget()
    }

    override suspend fun importOpml(feeds: List<OpmlFeed>): ImportResult {
        var added = 0
        var failed = 0
        val folderIds = mutableMapOf<String, Long?>()
        for (feed in feeds) {
            val folderId = feed.category?.let { name ->
                folderIds.getOrPut(name) { runCatching { ensureFolderId(name) }.getOrNull() }
            }
            runCatching { api.createFeed(feed.xmlUrl, folderId) }
                .onSuccess { added++ }
                .onFailure { failed++ }
        }
        if (added > 0) {
            reconcileFeeds()
        }
        return ImportResult(added = added, failed = failed)
    }

    // ---- Statusänderungen: sofort lokal, Push beim nächsten Sync (offline-first) ----

    override suspend fun markRead(articleIds: List<Long>, readAt: Long) {
        articleDao.markReadPending(articleIds, readAt)
    }

    override suspend fun markOpened(articleId: Long, timestamp: Long) {
        // E6: Öffnen archiviert lokal UND gilt am Server als gelesen.
        articleDao.markArchived(articleId, timestamp)
        articleDao.markReadPending(listOf(articleId), timestamp)
    }

    override suspend fun setBookmarked(articleId: Long, bookmarkedAt: Long?) {
        articleDao.setBookmarkedPending(articleId, bookmarkedAt)
    }

    /** Löscht den lokalen Nextcloud-Spiegel (beim Trennen mit Datenlöschung). */
    suspend fun wipeLocalMirror() {
        feedDao.deleteByOrigin(Origin.NEXTCLOUD)
        articleDao.deleteByOrigin(Origin.NEXTCLOUD)
    }

    /** Ordner-ID zum Kategorienamen, bei Bedarf am Server anlegen. */
    private suspend fun ensureFolderId(category: String): Long? {
        val existing = api.folders().firstOrNull { it.name.equals(category, ignoreCase = true) }
        if (existing != null) return existing.id
        return api.createFolder(category)?.id
    }

    /** POST /feeds mit 409/422 heißt: Server lehnt den Feed ab (Duplikat, ungültig). */
    private fun asFeedRejected(error: BackendException.ServerUnreachable): BackendException? {
        val cause = error.cause
        if (cause is NextcloudHttpRequestFailedException && cause.statusCode in FEED_REJECT_CODES) {
            return BackendException.FeedRejected(cause.statusCode)
        }
        return null
    }

    companion object {
        private const val MERGE_CHUNK_SIZE = 400
        private val FEED_REJECT_CODES = setOf(405, 409, 422)
    }
}
