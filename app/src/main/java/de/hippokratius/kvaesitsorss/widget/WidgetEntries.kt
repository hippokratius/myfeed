package de.hippokratius.kvaesitsorss.widget

import de.hippokratius.kvaesitsorss.core.filter.WordFilter
import de.hippokratius.kvaesitsorss.data.ArticleDao
import de.hippokratius.kvaesitsorss.data.ArticleEntity
import de.hippokratius.kvaesitsorss.data.FeedDao

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

/** Daten für ein Widget-Rendering: Einträge plus Favicon-Pfade je Feed. */
data class WidgetData(
    val entries: List<WidgetEntry>,
    /** feedId → Pfad des gecachten Feed-Logos. */
    val feedIconPaths: Map<Long, String>,
)

object WidgetEntries {

    const val MAX_ENTRIES = 40
    const val MAX_RELATED_SHOWN = 3
    const val SOURCE_LIMIT = 150

    suspend fun buildData(
        articleDao: ArticleDao,
        feedDao: FeedDao,
        category: String? = null,
        filterWords: Collection<String> = emptySet(),
    ): WidgetData {
        val icons = feedDao.getAll()
            .mapNotNull { feed -> feed.iconPath?.let { feed.id to it } }
            .toMap()
        return WidgetData(build(articleDao, category, filterWords), icons)
    }

    /**
     * Lädt die neuesten Artikel und formt sie in die Widget-Liste um.
     * Mit [category] werden nur Artikel von Feeds dieser Kategorie geladen.
     */
    suspend fun build(
        articleDao: ArticleDao,
        category: String? = null,
        filterWords: Collection<String> = emptySet(),
    ): List<WidgetEntry> {
        val articles = if (category == null) {
            articleDao.newest(limit = SOURCE_LIMIT)
        } else {
            articleDao.newestInCategory(category, limit = SOURCE_LIMIT)
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

        return entries.sortedByDescending { it.sortKey }.take(MAX_ENTRIES)
    }
}
