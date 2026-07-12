package de.hippokratius.kvaesitsorss.widget

import de.hippokratius.kvaesitsorss.data.ArticleDao
import de.hippokratius.kvaesitsorss.data.ArticleEntity
import de.hippokratius.kvaesitsorss.data.FeedDao

/** Anzeigemodell des Widgets: Einzelartikel oder Themen-Gruppe. */
sealed interface WidgetEntry {
    /** Neuester Zeitstempel, nach dem sortiert wird. */
    val sortKey: Long

    data class Single(val article: ArticleEntity) : WidgetEntry {
        override val sortKey: Long get() = article.publishedAt
    }

    data class Group(
        val groupId: String,
        val main: ArticleEntity,
        val related: List<ArticleEntity>,
        val extraCount: Int,
    ) : WidgetEntry {
        override val sortKey: Long get() = main.publishedAt
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

    suspend fun buildData(articleDao: ArticleDao, feedDao: FeedDao, category: String? = null): WidgetData {
        val icons = feedDao.getAll()
            .mapNotNull { feed -> feed.iconPath?.let { feed.id to it } }
            .toMap()
        return WidgetData(build(articleDao, category), icons)
    }

    /**
     * Formt die neuesten Artikel in die Widget-Liste um: Artikel mit gleicher
     * groupId werden zu einer Gruppen-Karte zusammengefasst (Hauptartikel =
     * neuester, mit Bild bevorzugt), alle anderen bleiben Einzelzeilen.
     * Mit [category] werden nur Artikel von Feeds dieser Kategorie geladen.
     */
    suspend fun build(articleDao: ArticleDao, category: String? = null): List<WidgetEntry> {
        val articles = if (category == null) {
            articleDao.newest(limit = 150)
        } else {
            articleDao.newestInCategory(category, limit = 150)
        }
        val byGroup = articles.filter { it.groupId != null }.groupBy { it.groupId!! }
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
                related = related.take(MAX_RELATED_SHOWN),
                extraCount = (related.size - MAX_RELATED_SHOWN).coerceAtLeast(0),
            )
        }

        articles.filter { it.groupId == null }.forEach { entries += WidgetEntry.Single(it) }

        return entries.sortedByDescending { it.sortKey }.take(MAX_ENTRIES)
    }
}
