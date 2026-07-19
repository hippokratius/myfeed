package de.hippokratius.myfeed.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE enabled = 1")
    suspend fun getEnabled(): List<FeedEntity>

    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<FeedEntity>

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Update
    suspend fun update(feed: FeedEntity)

    @Delete
    suspend fun delete(feed: FeedEntity)

    @Query("UPDATE feeds SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE feeds SET iconUrl = :iconUrl, iconPath = :iconPath WHERE id = :id")
    suspend fun updateIcon(id: Long, iconUrl: String?, iconPath: String?)

    @Query(
        "SELECT DISTINCT category FROM feeds WHERE category IS NOT NULL AND category != '' " +
            "ORDER BY category COLLATE NOCASE",
    )
    fun observeCategories(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT category FROM feeds WHERE category IS NOT NULL AND category != '' " +
            "ORDER BY category COLLATE NOCASE",
    )
    suspend fun getCategories(): List<String>

    @Query("UPDATE feeds SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String?)

    @Query("SELECT COUNT(*) FROM feeds WHERE category = :category")
    suspend fun countInCategory(category: String): Int
}

@Dao
interface ArticleDao {
    // [minPublishedAt] blendet Artikel aus, die nur noch wegen Archiv/Lesezeichen
    // aufbewahrt werden – der normale Feed zeigt nur Artikel innerhalb der
    // regulären Aufbewahrungsdauer.
    @Query(
        "SELECT * FROM articles WHERE publishedAt >= :minPublishedAt " +
            "ORDER BY publishedAt DESC LIMIT :limit",
    )
    suspend fun newest(limit: Int, minPublishedAt: Long): List<ArticleEntity>

    @Query(
        "SELECT * FROM articles WHERE publishedAt >= :minPublishedAt " +
            "ORDER BY publishedAt DESC",
    )
    fun observeAllNewest(minPublishedAt: Long): Flow<List<ArticleEntity>>

    @Query(
        "SELECT a.* FROM articles a JOIN feeds f ON a.feedId = f.id " +
            "WHERE f.category = :category AND a.publishedAt >= :minPublishedAt " +
            "ORDER BY a.publishedAt DESC LIMIT :limit",
    )
    suspend fun newestInCategory(category: String, limit: Int, minPublishedAt: Long): List<ArticleEntity>

    @Query(
        "SELECT a.* FROM articles a JOIN feeds f ON a.feedId = f.id " +
            "WHERE f.category = :category AND a.publishedAt >= :minPublishedAt " +
            "ORDER BY a.publishedAt DESC",
    )
    fun observeAllNewestInCategory(category: String, minPublishedAt: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE groupId = :groupId ORDER BY publishedAt DESC")
    fun observeGroup(groupId: String): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    /** Als gelesen markieren (nur einmal – ein gesetzter Zeitstempel bleibt stehen). */
    @Query("UPDATE articles SET readAt = :readAt WHERE id IN (:ids) AND readAt IS NULL")
    suspend fun markRead(ids: List<Long>, readAt: Long)

    /** Geöffnete Artikel wandern ins Archiv; der erste Zeitpunkt bleibt erhalten. */
    @Query("UPDATE articles SET archivedAt = :archivedAt WHERE id = :id AND archivedAt IS NULL")
    suspend fun markArchived(id: Long, archivedAt: Long)

    @Query("UPDATE articles SET bookmarkedAt = :bookmarkedAt WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarkedAt: Long?)

    @Query("SELECT * FROM articles WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC")
    fun observeBookmarked(): Flow<List<ArticleEntity>>

    /** Reguläres Aufräumen – archivierte und gemerkte Artikel bleiben verschont. */
    @Query(
        "DELETE FROM articles WHERE publishedAt < :minPublishedAt " +
            "AND archivedAt IS NULL AND bookmarkedAt IS NULL",
    )
    suspend fun deleteOlderThan(minPublishedAt: Long)

    /**
     * Aufräumen für Archiv und Lesezeichen mit getrennten Fristen: Ein Artikel
     * bleibt erhalten, solange ihn mindestens eine der beiden Aufbewahrungen
     * noch schützt – gelöscht wird erst, wenn das Archivieren älter als
     * [minArchivedAt] und das Lesezeichen älter als [minBookmarkedAt] ist.
     */
    @Query(
        "DELETE FROM articles WHERE (archivedAt IS NOT NULL OR bookmarkedAt IS NOT NULL) " +
            "AND (archivedAt IS NULL OR archivedAt < :minArchivedAt) " +
            "AND (bookmarkedAt IS NULL OR bookmarkedAt < :minBookmarkedAt)",
    )
    suspend fun deleteSavedOlderThan(minArchivedAt: Long, minBookmarkedAt: Long)

    @Query(
        "DELETE FROM articles WHERE archivedAt IS NULL AND bookmarkedAt IS NULL " +
            "AND id NOT IN (SELECT id FROM articles " +
            "WHERE archivedAt IS NULL AND bookmarkedAt IS NULL " +
            "ORDER BY publishedAt DESC LIMIT :maxCount)",
    )
    suspend fun enforceMaxCount(maxCount: Int)

    @Query("UPDATE articles SET groupId = NULL")
    suspend fun clearGroups()

    @Query("UPDATE articles SET groupId = :groupId WHERE id IN (:ids)")
    suspend fun setGroup(ids: List<Long>, groupId: String)

    @Query("UPDATE articles SET thumbPath = :path WHERE id = :id")
    suspend fun setThumbPath(id: Long, path: String?)

    @Query(
        "SELECT * FROM articles WHERE thumbPath IS NULL AND imageUrl IS NOT NULL " +
            "ORDER BY publishedAt DESC LIMIT :limit",
    )
    suspend fun withMissingThumbs(limit: Int): List<ArticleEntity>

    @Query("SELECT id FROM articles")
    suspend fun allIds(): List<Long>
}
