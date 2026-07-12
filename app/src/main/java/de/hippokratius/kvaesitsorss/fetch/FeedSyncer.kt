package de.hippokratius.kvaesitsorss.fetch

import android.content.Context
import android.util.Log
import de.hippokratius.kvaesitsorss.core.grouping.ClusterCandidate
import de.hippokratius.kvaesitsorss.core.grouping.TopicClusterer
import de.hippokratius.kvaesitsorss.core.model.ParsedFeed
import de.hippokratius.kvaesitsorss.core.rss.RssXmlParser
import de.hippokratius.kvaesitsorss.data.ArticleDao
import de.hippokratius.kvaesitsorss.data.ArticleEntity
import de.hippokratius.kvaesitsorss.data.FeedDao
import de.hippokratius.kvaesitsorss.data.FeedEntity
import de.hippokratius.kvaesitsorss.settings.SettingsRepository
import de.hippokratius.kvaesitsorss.widget.RssWidget
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Kern der Aktualisierung: lädt alle aktiven Feeds, aktualisiert die Datenbank,
 * lädt Thumbnails, berechnet die Themen-Gruppen und stößt das Widget-Update an.
 */
class FeedSyncer(
    private val context: Context,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val settingsRepository: SettingsRepository,
) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val thumbnailStore = ThumbnailStore(context, httpClient)
    private val clusterer = TopicClusterer()
    private val syncMutex = Mutex()

    /** Lädt einen Feed zur Validierung und liefert dessen Titel. */
    suspend fun probe(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        fetchAndParse(url)
    }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val settings = settingsRepository.current()
            val now = System.currentTimeMillis()

            for (feed in feedDao.getEnabled()) {
                runCatching { syncFeed(feed, now) }
                    .onFailure { Log.w(TAG, "Feed ${feed.url} konnte nicht geladen werden", it) }
            }

            articleDao.deleteOlderThan(now - TimeUnit.DAYS.toMillis(settings.maxAgeDays.toLong()))
            articleDao.enforceMaxCount(MAX_TOTAL_ARTICLES)

            if (settings.showImages) {
                downloadThumbnails()
            }
            thumbnailStore.prune(articleDao.allIds().toSet())

            regroup(settings.groupingEnabled)
            RssWidget.updateAll(context)
        }
    }

    /** Gruppierung neu berechnen (auch ohne Netz-Sync, z. B. nach Settings-Änderung). */
    suspend fun regroupAndRefreshWidget() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            regroup(settingsRepository.current().groupingEnabled)
            RssWidget.updateAll(context)
        }
    }

    private suspend fun syncFeed(feed: FeedEntity, now: Long) {
        val parsed = fetchAndParse(feed.url)

        if (feed.title.isBlank() && !parsed.title.isNullOrBlank()) {
            feedDao.updateTitle(feed.id, parsed.title!!)
        }
        val sourceTitle = feed.title.ifBlank { parsed.title ?: feed.url }

        val articles = parsed.items.map { item ->
            ArticleEntity(
                feedId = feed.id,
                sourceTitle = sourceTitle,
                guid = item.guid,
                title = item.title,
                link = item.link,
                imageUrl = item.imageUrl,
                thumbPath = null,
                publishedAt = item.publishedAtMillis ?: now,
                fetchedAt = now,
                groupId = null,
            )
        }
        articleDao.insertAll(articles)
    }

    private fun fetchAndParse(url: String): ParsedFeed {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code} für $url")
            }
            val body = response.body ?: throw java.io.IOException("Leere Antwort für $url")
            RssXmlParser.parse(body.byteStream())
        }
    }

    private suspend fun downloadThumbnails() {
        for (article in articleDao.withMissingThumbs(MAX_THUMB_DOWNLOADS_PER_SYNC)) {
            val path = thumbnailStore.download(article.id, article.imageUrl ?: continue)
            if (path != null) {
                articleDao.setThumbPath(article.id, path)
            }
        }
    }

    private suspend fun regroup(enabled: Boolean) {
        articleDao.clearGroups()
        if (!enabled) return

        val candidates = articleDao.newest(GROUPING_ARTICLE_LIMIT).map {
            ClusterCandidate(it.id, it.title, it.publishedAt)
        }
        for (group in clusterer.cluster(candidates)) {
            // Stabil genug: Gruppen-ID aus der kleinsten Artikel-ID der Gruppe.
            articleDao.setGroup(group, "g${group.min()}")
        }
    }

    companion object {
        private const val TAG = "FeedSyncer"
        const val USER_AGENT = "KvaesitsoRSS/1.0 (+https://github.com/hippokratius/kveasitso-rss)"
        private const val MAX_TOTAL_ARTICLES = 500
        private const val MAX_THUMB_DOWNLOADS_PER_SYNC = 40
        private const val GROUPING_ARTICLE_LIMIT = 300
    }
}
