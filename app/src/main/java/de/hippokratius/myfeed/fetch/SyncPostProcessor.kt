package de.hippokratius.myfeed.fetch

import android.content.Context
import de.hippokratius.myfeed.core.grouping.ClusterCandidate
import de.hippokratius.myfeed.core.grouping.TopicClusterer
import de.hippokratius.myfeed.data.ArticleDao
import de.hippokratius.myfeed.data.ArticleEntity
import de.hippokratius.myfeed.data.FeedDao
import de.hippokratius.myfeed.settings.AppSettings
import de.hippokratius.myfeed.settings.SettingsRepository
import de.hippokratius.myfeed.widget.RssWidget
import de.hippokratius.myfeed.widget.WidgetEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemeinsame Nachverarbeitung nach jedem Sync – identisch für lokale Feeds und
 * das Nextcloud-Backend: Aufbewahrung, Bestandslimit, Thumbnails, Themen-
 * Gruppierung, Sync-Zeitstempel und Widget-Update. Hält außerdem den einen
 * Sync-Mutex, den beide Backends um ihren gesamten Lauf legen.
 */
class SyncPostProcessor(
    private val context: Context,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val settingsRepository: SettingsRepository,
    private val thumbnailStore: ThumbnailStore,
) {

    private val clusterer = TopicClusterer()
    private val syncMutex = Mutex()

    /** Serialisiert Sync-Läufe und Regroup-Aufrufe (ein Lauf zur Zeit). */
    suspend fun <T> withSyncLock(block: suspend () -> T): T =
        syncMutex.withLock { block() }

    /**
     * Läuft am Ende eines Syncs, INNERHALB von [withSyncLock] (der Aufrufer
     * hält den Lock bereits um den gesamten Sync).
     */
    suspend fun run(origin: String) = withContext(Dispatchers.IO) {
        val settings = settingsRepository.current()
        val now = System.currentTimeMillis()

        // Archivierte und gemerkte Artikel überleben die normale Aufbewahrung
        // und werden erst nach ihrer jeweils eigenen Frist entfernt.
        articleDao.deleteOlderThan(settings.feedCutoffMillis(now), origin)
        articleDao.deleteSavedOlderThan(
            minArchivedAt = settings.archiveCutoffMillis(now),
            minBookmarkedAt = settings.bookmarkCutoffMillis(now),
            origin = origin,
        )
        articleDao.enforceMaxCount(MAX_TOTAL_ARTICLES, origin)

        if (settings.showImages) {
            downloadThumbnails(settings, origin)
        }
        // Cache-Aufräumen bewusst über BEIDE Herkünfte: Der inaktive Bestand
        // bleibt in der DB liegen und soll seine Bilder behalten.
        thumbnailStore.prune(
            validArticleIds = articleDao.allIds().toSet(),
            validFeedIds = feedDao.getAll().map { it.id }.toSet(),
        )

        regroup(settings, origin)
        settingsRepository.setLastSyncMillis(System.currentTimeMillis())
        RssWidget.updateAll(context)
    }

    /** Gruppierung neu berechnen (auch ohne Netz-Sync, z. B. nach Settings-Änderung). */
    suspend fun regroupAndRefreshWidget() = withContext(Dispatchers.IO) {
        withSyncLock {
            val settings = settingsRepository.current()
            regroup(settings, settings.activeOrigin)
            RssWidget.updateAll(context)
        }
    }

    /**
     * Thumbnails werden nur für Artikel vorab geladen, deren Bitmaps ein
     * tatsächlich platziertes Widget rendert – Widgets können nicht lazy laden.
     * Die App selbst lädt Bilder on-demand beim Scrollen (Coil-Cache), ohne
     * platzierte Widgets entsteht beim Sync also kein Bild-Traffic.
     */
    private suspend fun downloadThumbnails(settings: AppSettings, origin: String) {
        val categories = RssWidget.configuredCategories(context)
        if (categories.isEmpty()) return

        val cutoff = settings.feedCutoffMillis(System.currentTimeMillis())
        val candidates = LinkedHashMap<Long, ArticleEntity>()
        for (category in categories.distinct()) {
            val entries = WidgetEntries.build(articleDao, category, settings.filterWords, cutoff, origin)
            for (article in WidgetEntries.thumbCandidates(entries)) {
                if (article.thumbPath == null && article.imageUrl != null) {
                    candidates.putIfAbsent(article.id, article)
                }
            }
        }

        for (article in candidates.values.take(MAX_THUMB_DOWNLOADS_PER_SYNC)) {
            val path = thumbnailStore.download(article.id, article.imageUrl ?: continue)
            if (path != null) {
                articleDao.setThumbPath(article.id, path)
            }
        }
    }

    private suspend fun regroup(settings: AppSettings, origin: String) {
        articleDao.clearGroups(origin)
        if (!settings.groupingEnabled) return

        // Nur Artikel innerhalb der normalen Aufbewahrung gruppieren – alte
        // Archiv-/Lesezeichen-Artikel tauchen im Feed nicht mehr auf.
        val cutoff = settings.feedCutoffMillis(System.currentTimeMillis())
        val candidates = articleDao.newest(GROUPING_ARTICLE_LIMIT, cutoff, origin).map {
            ClusterCandidate(it.id, it.title, it.publishedAt, sourceKey = it.feedId.toString())
        }
        for (group in clusterer.cluster(candidates)) {
            // Stabil genug: Gruppen-ID aus der kleinsten Artikel-ID der Gruppe.
            articleDao.setGroup(group, "g${group.min()}")
        }
    }

    companion object {
        const val MAX_TOTAL_ARTICLES = 500
        private const val MAX_THUMB_DOWNLOADS_PER_SYNC = 40
        private const val GROUPING_ARTICLE_LIMIT = 300
    }
}
