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
    @Query("SELECT * FROM feeds WHERE origin = :origin ORDER BY title COLLATE NOCASE")
    fun observeAll(origin: String): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE origin = :origin")
    suspend fun getByOrigin(origin: String): List<FeedEntity>

    /** Beide Herkünfte – nur fürs Aufräumen von Caches, nie für die Anzeige. */
    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<FeedEntity>

    @Query("SELECT COUNT(*) FROM feeds WHERE origin = :origin")
    suspend fun count(origin: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(feeds: List<FeedEntity>): List<Long>

    @Update
    suspend fun update(feed: FeedEntity)

    @Delete
    suspend fun delete(feed: FeedEntity)

    @Query("UPDATE feeds SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE feeds SET iconUrl = :iconUrl, iconPath = :iconPath WHERE id = :id")
    suspend fun updateIcon(id: Long, iconUrl: String?, iconPath: String?)

    @Query(
        "SELECT DISTINCT category FROM feeds WHERE origin = :origin " +
            "AND category IS NOT NULL AND category != '' ORDER BY category COLLATE NOCASE",
    )
    fun observeCategories(origin: String): Flow<List<String>>

    @Query(
        "SELECT DISTINCT category FROM feeds WHERE origin = :origin " +
            "AND category IS NOT NULL AND category != '' ORDER BY category COLLATE NOCASE",
    )
    suspend fun getCategories(origin: String): List<String>

    @Query("UPDATE feeds SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String?)

    @Query("SELECT COUNT(*) FROM feeds WHERE category = :category AND origin = :origin")
    suspend fun countInCategory(category: String, origin: String): Int

    // ---- Nextcloud-Spiegel ----

    @Query("SELECT * FROM feeds WHERE origin = :origin AND remoteId = :remoteId")
    suspend fun byRemoteId(remoteId: Long, origin: String = Origin.NEXTCLOUD): FeedEntity?

    @Query(
        "UPDATE feeds SET title = :title, category = :category, iconUrl = :iconUrl " +
            "WHERE origin = :origin AND remoteId = :remoteId",
    )
    suspend fun updateFromServer(
        remoteId: Long,
        title: String,
        category: String?,
        iconUrl: String?,
        origin: String = Origin.NEXTCLOUD,
    )

    @Query("DELETE FROM feeds WHERE origin = :origin AND remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(remoteIds: List<Long>, origin: String = Origin.NEXTCLOUD)

    @Query("DELETE FROM feeds WHERE origin = :origin")
    suspend fun deleteByOrigin(origin: String)
}

@Dao
interface ArticleDao {
    // [minPublishedAt] blendet Artikel aus, die nur noch wegen Archiv/Lesezeichen
    // aufbewahrt werden – der normale Feed zeigt nur Artikel innerhalb der
    // regulären Aufbewahrungsdauer.
    @Query(
        "SELECT * FROM articles WHERE origin = :origin AND publishedAt >= :minPublishedAt " +
            "ORDER BY publishedAt DESC LIMIT :limit",
    )
    suspend fun newest(limit: Int, minPublishedAt: Long, origin: String): List<ArticleEntity>

    @Query(
        "SELECT * FROM articles WHERE origin = :origin AND publishedAt >= :minPublishedAt " +
            "ORDER BY publishedAt DESC",
    )
    fun observeAllNewest(minPublishedAt: Long, origin: String): Flow<List<ArticleEntity>>

    @Query(
        "SELECT a.* FROM articles a JOIN feeds f ON a.feedId = f.id " +
            "WHERE f.category = :category AND a.origin = :origin " +
            "AND a.publishedAt >= :minPublishedAt " +
            "ORDER BY a.publishedAt DESC LIMIT :limit",
    )
    suspend fun newestInCategory(
        category: String,
        limit: Int,
        minPublishedAt: Long,
        origin: String,
    ): List<ArticleEntity>

    @Query(
        "SELECT a.* FROM articles a JOIN feeds f ON a.feedId = f.id " +
            "WHERE f.category = :category AND a.origin = :origin " +
            "AND a.publishedAt >= :minPublishedAt " +
            "ORDER BY a.publishedAt DESC",
    )
    fun observeAllNewestInCategory(
        category: String,
        minPublishedAt: Long,
        origin: String,
    ): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE groupId = :groupId ORDER BY publishedAt DESC")
    fun observeGroup(groupId: String): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    @Update
    suspend fun updateAll(articles: List<ArticleEntity>)

    /** Als gelesen markieren (nur einmal – ein gesetzter Zeitstempel bleibt stehen). */
    @Query("UPDATE articles SET readAt = :readAt WHERE id IN (:ids) AND readAt IS NULL")
    suspend fun markRead(ids: List<Long>, readAt: Long)

    /**
     * Wie [markRead], merkt die Änderung aber zusätzlich für den Server-Push vor
     * (nur für gespiegelte Artikel mit remoteId sinnvoll).
     */
    @Query(
        "UPDATE articles SET pendingReadSync = CASE " +
            "WHEN readAt IS NULL AND remoteId IS NOT NULL THEN 1 ELSE pendingReadSync END, " +
            "readAt = COALESCE(readAt, :readAt) WHERE id IN (:ids)",
    )
    suspend fun markReadPending(ids: List<Long>, readAt: Long)

    /** Geöffnete Artikel wandern ins Archiv; der erste Zeitpunkt bleibt erhalten. */
    @Query("UPDATE articles SET archivedAt = :archivedAt WHERE id = :id AND archivedAt IS NULL")
    suspend fun markArchived(id: Long, archivedAt: Long)

    @Query("UPDATE articles SET bookmarkedAt = :bookmarkedAt WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarkedAt: Long?)

    /** Wie [setBookmarked], mit Vormerkung für den Stern-Push zum Server. */
    @Query(
        "UPDATE articles SET bookmarkedAt = :bookmarkedAt, pendingStarSync = CASE " +
            "WHEN remoteId IS NOT NULL THEN 1 ELSE pendingStarSync END WHERE id = :id",
    )
    suspend fun setBookmarkedPending(id: Long, bookmarkedAt: Long?)

    @Query(
        "SELECT * FROM articles WHERE origin = :origin AND archivedAt IS NOT NULL " +
            "ORDER BY archivedAt DESC",
    )
    fun observeArchived(origin: String): Flow<List<ArticleEntity>>

    @Query(
        "SELECT * FROM articles WHERE origin = :origin AND bookmarkedAt IS NOT NULL " +
            "ORDER BY bookmarkedAt DESC",
    )
    fun observeBookmarked(origin: String): Flow<List<ArticleEntity>>

    // Aufräum-Queries verschonen Zeilen mit Pending-Flags: Eine noch nicht
    // gepushte Gelesen-/Stern-Änderung darf nicht verloren gehen (E2-Schutz).

    /** Reguläres Aufräumen – archivierte und gemerkte Artikel bleiben verschont. */
    @Query(
        "DELETE FROM articles WHERE origin = :origin AND publishedAt < :minPublishedAt " +
            "AND archivedAt IS NULL AND bookmarkedAt IS NULL " +
            "AND pendingReadSync = 0 AND pendingStarSync = 0",
    )
    suspend fun deleteOlderThan(minPublishedAt: Long, origin: String)

    /**
     * Aufräumen für Archiv und Lesezeichen mit getrennten Fristen: Ein Artikel
     * bleibt erhalten, solange ihn mindestens eine der beiden Aufbewahrungen
     * noch schützt – gelöscht wird erst, wenn das Archivieren älter als
     * [minArchivedAt] und das Lesezeichen älter als [minBookmarkedAt] ist.
     */
    @Query(
        "DELETE FROM articles WHERE origin = :origin " +
            "AND (archivedAt IS NOT NULL OR bookmarkedAt IS NOT NULL) " +
            "AND (archivedAt IS NULL OR archivedAt < :minArchivedAt) " +
            "AND (bookmarkedAt IS NULL OR bookmarkedAt < :minBookmarkedAt) " +
            "AND pendingReadSync = 0 AND pendingStarSync = 0",
    )
    suspend fun deleteSavedOlderThan(minArchivedAt: Long, minBookmarkedAt: Long, origin: String)

    @Query(
        "DELETE FROM articles WHERE origin = :origin " +
            "AND archivedAt IS NULL AND bookmarkedAt IS NULL " +
            "AND pendingReadSync = 0 AND pendingStarSync = 0 " +
            "AND id NOT IN (SELECT id FROM articles " +
            "WHERE origin = :origin AND archivedAt IS NULL AND bookmarkedAt IS NULL " +
            "ORDER BY publishedAt DESC LIMIT :maxCount)",
    )
    suspend fun enforceMaxCount(maxCount: Int, origin: String)

    @Query("UPDATE articles SET groupId = NULL WHERE origin = :origin")
    suspend fun clearGroups(origin: String)

    @Query("UPDATE articles SET groupId = :groupId WHERE id IN (:ids)")
    suspend fun setGroup(ids: List<Long>, groupId: String)

    @Query("UPDATE articles SET thumbPath = :path WHERE id = :id")
    suspend fun setThumbPath(id: Long, path: String?)

    /** Beide Herkünfte – nur fürs Aufräumen des Thumbnail-Caches. */
    @Query("SELECT id FROM articles")
    suspend fun allIds(): List<Long>

    // ---- Nextcloud-Sync ----

    @Query("SELECT * FROM articles WHERE remoteId IN (:remoteIds)")
    suspend fun byRemoteIds(remoteIds: List<Long>): List<ArticleEntity>

    @Query(
        "SELECT remoteId FROM articles WHERE pendingReadSync = 1 AND remoteId IS NOT NULL",
    )
    suspend fun pendingReadRemoteIds(): List<Long>

    @Query(
        "SELECT remoteId FROM articles WHERE pendingStarSync = 1 " +
            "AND bookmarkedAt IS NOT NULL AND remoteId IS NOT NULL",
    )
    suspend fun pendingStarRemoteIds(): List<Long>

    @Query(
        "SELECT remoteId FROM articles WHERE pendingStarSync = 1 " +
            "AND bookmarkedAt IS NULL AND remoteId IS NOT NULL",
    )
    suspend fun pendingUnstarRemoteIds(): List<Long>

    @Query("UPDATE articles SET pendingReadSync = 0 WHERE remoteId IN (:remoteIds)")
    suspend fun clearPendingRead(remoteIds: List<Long>)

    @Query("UPDATE articles SET pendingStarSync = 0 WHERE remoteId IN (:remoteIds)")
    suspend fun clearPendingStar(remoteIds: List<Long>)

    @Query("UPDATE articles SET sourceTitle = :sourceTitle WHERE feedId = :feedId")
    suspend fun updateSourceTitle(feedId: Long, sourceTitle: String)

    // ---- Server-Lebenszyklus (§4.3, Opt-in) ----

    /** Abgelaufene ungelesene Artikel: lokal als gelesen markieren + Push vormerken. */
    @Query(
        "UPDATE articles SET readAt = :readAt, pendingReadSync = 1 " +
            "WHERE origin = :origin AND readAt IS NULL " +
            "AND publishedAt < :minPublishedAt AND remoteId IS NOT NULL",
    )
    suspend fun expireUnread(minPublishedAt: Long, readAt: Long, origin: String = Origin.NEXTCLOUD)

    /** Abgelaufene Lesezeichen: lokal entsternen + Entstern-Push vormerken. */
    @Query(
        "UPDATE articles SET bookmarkedAt = NULL, pendingStarSync = 1 " +
            "WHERE origin = :origin AND bookmarkedAt IS NOT NULL " +
            "AND bookmarkedAt < :minBookmarkedAt AND remoteId IS NOT NULL",
    )
    suspend fun expireBookmarks(minBookmarkedAt: Long, origin: String = Origin.NEXTCLOUD)

    @Query("DELETE FROM articles WHERE origin = :origin")
    suspend fun deleteByOrigin(origin: String)
}
