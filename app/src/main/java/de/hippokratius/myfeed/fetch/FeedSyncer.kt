package de.hippokratius.myfeed.fetch

import android.content.Context
import android.util.Log
import de.hippokratius.myfeed.core.catalog.FeedUrls
import de.hippokratius.myfeed.core.discovery.DiscoveredFeed
import de.hippokratius.myfeed.core.discovery.FeedLinkFinder
import de.hippokratius.myfeed.core.grouping.ClusterCandidate
import de.hippokratius.myfeed.core.grouping.TopicClusterer
import de.hippokratius.myfeed.core.model.ParsedFeed
import de.hippokratius.myfeed.core.rss.RssXmlParser
import de.hippokratius.myfeed.data.ArticleDao
import de.hippokratius.myfeed.data.ArticleEntity
import de.hippokratius.myfeed.data.FeedDao
import de.hippokratius.myfeed.data.FeedEntity
import de.hippokratius.myfeed.settings.AppSettings
import de.hippokratius.myfeed.settings.SettingsRepository
import de.hippokratius.myfeed.widget.RssWidget
import de.hippokratius.myfeed.widget.WidgetEntries
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Ergebnis der Auflösung einer Nutzereingabe im "Feed hinzufügen"-Dialog. */
sealed class FeedResolution {
    /** Die eingegebene URL ist selbst ein Feed. */
    data class Direct(val url: String, val feed: ParsedFeed) : FeedResolution()

    /** Die URL ist eine HTML-Seite mit mindestens einem verifizierten Feed. */
    data class Discovered(val candidates: List<DiscoveredFeed>) : FeedResolution()

    /** Seite geladen, aber kein funktionierender Feed gefunden. */
    data object NoFeedsFound : FeedResolution()
}

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

    /** Für die Feed-Suche im Dialog: harte Gesamtdauer pro Request statt Minuten-Timeouts. */
    private val discoveryClient: OkHttpClient = httpClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val thumbnailStore = ThumbnailStore(context, httpClient)
    private val clusterer = TopicClusterer()
    private val syncMutex = Mutex()

    /**
     * Löst die Nutzereingabe aus dem "Feed hinzufügen"-Dialog auf: Ist die URL selbst ein
     * Feed, kommt [FeedResolution.Direct] zurück. Ist es eine HTML-Seite, werden dort
     * verlinkte Feeds gesucht (Fallback: gängige Pfade wie /feed), verifiziert und als
     * Vorschläge geliefert. Ein fehlendes Scheme wird mit https:// ergänzt.
     * Wirft IOException bei Netzwerkfehlern, IllegalArgumentException bei kaputter URL.
     */
    suspend fun resolveFeedInput(input: String): FeedResolution = withContext(Dispatchers.IO) {
        val url = input.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }

        val (bytes, headerCharset, finalUrl) =
            discoveryClient.newCall(buildRequest(url, ACCEPT_DISCOVERY)).execute().use { response ->
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP ${response.code} für $url")
                }
                val body = response.body ?: throw java.io.IOException("Leere Antwort für $url")
                val source = body.source()
                if (source.request(MAX_RESPONSE_BYTES + 1L)) {
                    throw java.io.IOException("Antwort größer als $MAX_RESPONSE_BYTES Bytes für $url")
                }
                Triple(
                    source.buffer.readByteArray(),
                    body.contentType()?.charset(),
                    response.request.url.toString(),
                )
            }

        // Weiche nach Inhalt, nicht nach Content-Type: manche Feeds kommen als text/html.
        val direct = runCatching { RssXmlParser.parse(ByteArrayInputStream(bytes)) }.getOrNull()
        if (direct != null) {
            return@withContext FeedResolution.Direct(url, direct)
        }

        // Content-Type ohne charset-Angabe: Deklaration aus dem Seitenkopf lesen (Umlaute!).
        val charset = headerCharset
            ?: FeedLinkFinder.detectCharset(String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1))
                ?.let { name -> runCatching { charset(name) }.getOrNull() }
            ?: Charsets.UTF_8

        val triedUrls = mutableSetOf<String>()
        var verified = verifyCandidates(FeedLinkFinder.find(String(bytes, charset), finalUrl), triedUrls)
        if (verified.isEmpty()) {
            // Nichts (Funktionierendes) deklariert: gängige Pfade an der Site-Root probieren.
            val fallback = FeedLinkFinder.commonFeedPaths(finalUrl).map { DiscoveredFeed(it) }
            verified = verifyCandidates(fallback, triedUrls)
        }

        if (verified.isEmpty()) FeedResolution.NoFeedsFound else FeedResolution.Discovered(verified)
    }

    /** Lädt Kandidaten parallel und behält nur parsebare Feeds, ohne Alias-Duplikate. */
    private suspend fun verifyCandidates(
        candidates: List<DiscoveredFeed>,
        triedUrls: MutableSet<String>,
    ): List<DiscoveredFeed> = coroutineScope {
        val fresh = candidates.filter { triedUrls.add(FeedUrls.canonical(it.url)) }
        val fetched = fresh.map { candidate ->
            async { candidate to runCatching { fetchAndParse(candidate.url, discoveryClient) }.getOrNull() }
        }.awaitAll()

        val seenContent = mutableSetOf<String>()
        buildList {
            for ((candidate, parsed) in fetched) {
                if (parsed == null) continue
                // Alias-Pfade (z. B. /rss als Redirect auf /rss.xml) liefern identische
                // Einträge; unterschiedliche Feeds unterscheiden sich in der GUID-Liste.
                val signature = parsed.items.joinToString("|") { it.guid }
                    .ifBlank { parsed.title ?: candidate.url }
                if (!seenContent.add(signature)) continue
                add(DiscoveredFeed(candidate.url, candidate.title ?: parsed.title))
            }
        }
    }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val settings = settingsRepository.current()
            val now = System.currentTimeMillis()

            for (feed in feedDao.getEnabled()) {
                runCatching { syncFeed(feed, now) }
                    .onFailure { Log.w(TAG, "Feed ${feed.url} konnte nicht geladen werden", it) }
            }

            // Archivierte und gemerkte Artikel überleben die normale Aufbewahrung
            // und werden erst nach ihrer jeweils eigenen Frist entfernt.
            articleDao.deleteOlderThan(settings.feedCutoffMillis(now))
            articleDao.deleteSavedOlderThan(
                minArchivedAt = settings.archiveCutoffMillis(now),
                minBookmarkedAt = settings.bookmarkCutoffMillis(now),
            )
            articleDao.enforceMaxCount(MAX_TOTAL_ARTICLES)

            if (settings.showImages) {
                downloadThumbnails(settings)
            }
            thumbnailStore.prune(
                validArticleIds = articleDao.allIds().toSet(),
                validFeedIds = feedDao.getAll().map { it.id }.toSet(),
            )

            regroup(settings)
            settingsRepository.setLastSyncMillis(System.currentTimeMillis())
            RssWidget.updateAll(context)
        }
    }

    /** Gruppierung neu berechnen (auch ohne Netz-Sync, z. B. nach Settings-Änderung). */
    suspend fun regroupAndRefreshWidget() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            regroup(settingsRepository.current())
            RssWidget.updateAll(context)
        }
    }

    private suspend fun syncFeed(feed: FeedEntity, now: Long) {
        val parsed = fetchAndParse(feed.url)

        if (feed.title.isBlank() && !parsed.title.isNullOrBlank()) {
            feedDao.updateTitle(feed.id, parsed.title!!)
        }
        val sourceTitle = feed.title.ifBlank { parsed.title ?: feed.url }

        // Feed-Logo cachen, wenn neu oder geändert.
        val iconUrl = parsed.iconUrl
        if (iconUrl != null && (iconUrl != feed.iconUrl || feed.iconPath == null)) {
            val path = thumbnailStore.downloadIcon(feed.id, iconUrl)
            if (path != null) {
                feedDao.updateIcon(feed.id, iconUrl, path)
            }
        }

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

    private fun fetchAndParse(url: String, client: OkHttpClient = httpClient): ParsedFeed {
        return client.newCall(buildRequest(url, ACCEPT_FEED)).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code} für $url")
            }
            val body = response.body ?: throw java.io.IOException("Leere Antwort für $url")
            RssXmlParser.parse(body.byteStream())
        }
    }

    private fun buildRequest(url: String, accept: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", accept)
        .build()

    /**
     * Thumbnails werden nur noch für Artikel vorab geladen, deren Bitmaps ein
     * tatsächlich platziertes Widget rendert – Widgets können nicht lazy laden.
     * Die App selbst lädt Bilder on-demand beim Scrollen (Coil-Cache), ohne
     * platzierte Widgets entsteht beim Sync also kein Bild-Traffic.
     */
    private suspend fun downloadThumbnails(settings: AppSettings) {
        val categories = RssWidget.configuredCategories(context)
        if (categories.isEmpty()) return

        val cutoff = settings.feedCutoffMillis(System.currentTimeMillis())
        val candidates = LinkedHashMap<Long, ArticleEntity>()
        for (category in categories.distinct()) {
            val entries = WidgetEntries.build(articleDao, category, settings.filterWords, cutoff)
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

    private suspend fun regroup(settings: AppSettings) {
        articleDao.clearGroups()
        if (!settings.groupingEnabled) return

        // Nur Artikel innerhalb der normalen Aufbewahrung gruppieren – alte
        // Archiv-/Lesezeichen-Artikel tauchen im Feed nicht mehr auf.
        val cutoff = settings.feedCutoffMillis(System.currentTimeMillis())
        val candidates = articleDao.newest(GROUPING_ARTICLE_LIMIT, cutoff).map {
            ClusterCandidate(it.id, it.title, it.publishedAt, sourceKey = it.feedId.toString())
        }
        for (group in clusterer.cluster(candidates)) {
            // Stabil genug: Gruppen-ID aus der kleinsten Artikel-ID der Gruppe.
            articleDao.setGroup(group, "g${group.min()}")
        }
    }

    companion object {
        private const val TAG = "FeedSyncer"
        const val USER_AGENT = "MyFeed/1.0 (+https://github.com/hippokratius/kveasitso-rss)"
        private const val ACCEPT_FEED =
            "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
        private const val ACCEPT_DISCOVERY = "$ACCEPT_FEED, text/html"
        private const val MAX_RESPONSE_BYTES = 10L * 1024 * 1024
        private const val MAX_TOTAL_ARTICLES = 500
        private const val MAX_THUMB_DOWNLOADS_PER_SYNC = 40
        private const val GROUPING_ARTICLE_LIMIT = 300
    }
}
