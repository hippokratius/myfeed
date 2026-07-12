package de.hippokratius.kvaesitsorss.data

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
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun newest(limit: Int): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE groupId = :groupId ORDER BY publishedAt DESC")
    fun observeGroup(groupId: String): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    @Query("DELETE FROM articles WHERE publishedAt < :minPublishedAt")
    suspend fun deleteOlderThan(minPublishedAt: Long)

    @Query(
        "DELETE FROM articles WHERE id NOT IN " +
            "(SELECT id FROM articles ORDER BY publishedAt DESC LIMIT :maxCount)",
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
