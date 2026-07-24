package de.hippokratius.myfeed.widget

import de.hippokratius.myfeed.core.filter.WordFilter
import de.hippokratius.myfeed.data.ArticleDao
import de.hippokratius.myfeed.data.ArticleEntity
import de.hippokratius.myfeed.data.FeedDao

/** Anzeigemodell für Widget und App-Reader: Einzelartikel oder Themen-Gruppe. */
sealed interface WidgetEntry {
    /** Neuester Zeitstempel, nach dem sortiert wird. */
    val sortKey: Long

    /** Stabile ID für Lazy-Listen; Gruppen negativ, damit kollisionsfrei zu Artikel-IDs. */
    val stableId: Long

    data class Single(val article: ArticleEntity) : WidgetEntry {
        override val sortKey: Long get() = article.publishedAt
        override val stableId: Long get() = article.id
    }

    data class Group(
        val groupId: String,
        val main: ArticleEntity,
        val related: List<ArticleEntity>,
        val extraCount: Int,
    ) : WidgetEntry {
        override val sortKey: Long get() = main.publishedAt
        override val stableId: Long get() = -(main.id + 1)
    }
}

/** Alle in diesem Eintrag gezeigten Artikel (Gruppe: Hauptartikel + sichtbare verwandte). */
fun WidgetEntry.articles(): List<ArticleEntity> = when (this) {
    is WidgetEntry.Single -> listOf(article)
    is WidgetEntry.Group -> listOf(main) + related
}

/** Daten für ein Widget-Rendering: Einträge plus Favicon-Pfade je Feed. */
data class WidgetData(
    val entries: List<WidgetEntry>,
    /** feedId → Pfad des gecachten Feed-Logos. */
    val feedIconPaths: Map<Long, String>,
)

object WidgetEntries {

    /** Nur fürs Widget: RemoteViews vertragen keine beliebig langen Listen. */
    const val MAX_ENTRIES = 40
    const val MAX_RELATED_SHOWN = 3
    const val SOURCE_LIMIT = 150

    // Bitmap-Budget des Widgets (RemoteViews-Speicher ist knapp). Bestimmt
    // zugleich, welche Thumbnails der Sync vorab herunterlädt.
    const val MAX_ARTICLE_BITMAPS = 14
    const val MAX_RELATED_BITMAPS = 12
    const val MAX_GROUPS_WITH_THUMBS = 4

    suspend fun buildData(
        articleDao: ArticleDao,
        feedDao: FeedDao,
        category: String? = null,
        filterWords: Collection<String> = emptySet(),
        minPublishedAt: Long = 0,
        origin: String,
    ): WidgetData {
        val icons = feedDao.getByOrigin(origin)
            .mapNotNull { feed -> feed.iconPath?.let { feed.id to it } }
            .toMap()
        return WidgetData(build(articleDao, category, filterWords, minPublishedAt, origin), icons)
    }

    /**
     * Lädt die neuesten Artikel und formt sie in die Widget-Liste um.
     * Mit [category] werden nur Artikel von Feeds dieser Kategorie geladen.
     */
    suspend fun build(
        articleDao: ArticleDao,
        category: String? = null,
        filterWords: Collection<String> = emptySet(),
        minPublishedAt: Long = 0,
        origin: String,
    ): List<WidgetEntry> {
        val articles = if (category == null) {
            articleDao.newest(limit = SOURCE_LIMIT, minPublishedAt = minPublishedAt, origin = origin)
        } else {
            articleDao.newestInCategory(
                category,
                limit = SOURCE_LIMIT,
                minPublishedAt = minPublishedAt,
                origin = origin,
            )
        }
        return fromArticles(articles, filterWords = filterWords)
    }

    /**
     * Formt Artikel in die Anzeige-Liste um: Artikel mit gleicher groupId
     * werden zu einer Gruppen-Karte zusammengefasst (Hauptartikel = neuester,
     * mit Bild bevorzugt), alle anderen bleiben Einzelzeilen. [maxRelated]
     * begrenzt die direkt sichtbaren verwandten Artikel je Gruppe.
     * Artikel, deren Titel ein Wort aus [filterWords] enthält, werden ausgeblendet.
     */
    fun fromArticles(
        articles: List<ArticleEntity>,
        maxRelated: Int = MAX_RELATED_SHOWN,
        filterWords: Collection<String> = emptySet(),
        /** Der Reader zeigt alle Artikel, das Widget kappt bei [MAX_ENTRIES]. */
        maxEntries: Int = MAX_ENTRIES,
    ): List<WidgetEntry> {
        val filter = WordFilter(filterWords)
        val visible = if (filter.isEmpty) articles else articles.filterNot { filter.matches(it.title) }
        val byGroup = visible.filter { it.groupId != null }.groupBy { it.groupId!! }
        val entries = mutableListOf<WidgetEntry>()

        for ((groupId, members) in byGroup) {
            if (members.size < 2) {
                members.forEach { entries += WidgetEntry.Single(it) }
                continue
            }
            val sorted = members.sortedByDescending { it.publishedAt }
            val main = sorted.firstOrNull { it.thumbPath != null || it.imageUrl != null } ?: sorted.first()
            val related = sorted.filter { it.id != main.id }
            entries += WidgetEntry.Group(
                groupId = groupId,
                main = main,
                related = related.take(maxRelated),
                extraCount = (related.size - maxRelated).coerceAtLeast(0),
            )
        }

        visible.filter { it.groupId == null }.forEach { entries += WidgetEntry.Single(it) }

        return entries.sortedByDescending { it.sortKey }.take(maxEntries)
    }

    /**
     * Genau die Artikel, für die das Widget Bitmaps rendert (Haupt-/Einzel-
     * artikel bis [MAX_ARTICLE_BITMAPS], verwandte Artikel der obersten
     * [MAX_GROUPS_WITH_THUMBS] Gruppen bis [MAX_RELATED_BITMAPS]) – nur für
     * diese lädt der Sync Thumbnails vorab herunter.
     */
    fun thumbCandidates(entries: List<WidgetEntry>): List<ArticleEntity> {
        val candidates = mutableListOf<ArticleEntity>()
        entries.take(MAX_ARTICLE_BITMAPS).forEach { entry ->
            candidates += when (entry) {
                is WidgetEntry.Single -> entry.article
                is WidgetEntry.Group -> entry.main
            }
        }
        var relatedCount = 0
        for (entry in entries.filterIsInstance<WidgetEntry.Group>().take(MAX_GROUPS_WITH_THUMBS)) {
            for (related in entry.related) {
                if (relatedCount >= MAX_RELATED_BITMAPS) break
                candidates += related
                relatedCount++
            }
        }
        return candidates
    }
}
