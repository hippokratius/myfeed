package de.hippokratius.myfeed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN iconUrl TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN iconPath TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN category TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feeds_category ON feeds(category)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN readAt INTEGER")
                db.execSQL("ALTER TABLE articles ADD COLUMN archivedAt INTEGER")
                db.execSQL("ALTER TABLE articles ADD COLUMN bookmarkedAt INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_articles_archivedAt ON articles(archivedAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_articles_bookmarkedAt ON articles(bookmarkedAt)",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            // Dateiname bleibt trotz Umbenennung in "MyFeed" unverändert, damit
            // bestehende Installationen ihre Feeds und Artikel behalten.
            Room.databaseBuilder(context, AppDatabase::class.java, "kvaesitso-rss.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
