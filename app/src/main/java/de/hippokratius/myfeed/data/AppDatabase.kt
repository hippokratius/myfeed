package de.hippokratius.myfeed.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class],
    version = 6,
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

        /**
         * Reine Daten-Reparatur: Ein Bug im Lese-Tracking hatte beim Einfügen
         * neuer Artikel oberhalb der Scroll-Position massenhaft Artikel
         * fälschlich als gelesen markiert – einmalig zurücksetzen.
         * Archiv und Lesezeichen bleiben unberührt.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE articles SET readAt = NULL")
            }
        }

        /**
         * Nextcloud-Integration: Herkunfts-Spalte (origin) trennt lokale Feeds vom
         * Nextcloud-Spiegel, remoteId verweist auf die Server-IDs, Pending-Flags
         * merken ungepushte Gelesen-/Stern-Änderungen. feeds wird neu aufgebaut,
         * weil der Unique-Index von (url) auf (origin, url) wechselt und der
         * Pro-Feed-Aktiv-Schalter (enabled) ersatzlos entfällt.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feeds_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`iconUrl` TEXT, " +
                        "`iconPath` TEXT, " +
                        "`category` TEXT, " +
                        "`origin` TEXT NOT NULL DEFAULT 'LOCAL', " +
                        "`remoteId` INTEGER)",
                )
                db.execSQL(
                    "INSERT INTO feeds_new (id, url, title, iconUrl, iconPath, category) " +
                        "SELECT id, url, title, iconUrl, iconPath, category FROM feeds",
                )
                db.execSQL("DROP TABLE feeds")
                db.execSQL("ALTER TABLE feeds_new RENAME TO feeds")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_feeds_origin_url` " +
                        "ON `feeds` (`origin`, `url`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_feeds_origin_remoteId` " +
                        "ON `feeds` (`origin`, `remoteId`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_feeds_category` ON `feeds` (`category`)")

                db.execSQL("ALTER TABLE articles ADD COLUMN origin TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL("ALTER TABLE articles ADD COLUMN remoteId INTEGER")
                db.execSQL("ALTER TABLE articles ADD COLUMN pendingReadSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN pendingStarSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_remoteId` " +
                        "ON `articles` (`remoteId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_articles_origin_publishedAt` " +
                        "ON `articles` (`origin`, `publishedAt`)",
                )

                // Rebuild-Absicherung: FK articles.feedId -> feeds.id muss intakt sein.
                db.query("PRAGMA foreign_key_check(`articles`)").use { cursor ->
                    check(!cursor.moveToFirst()) {
                        "foreign_key_check nach feeds-Rebuild fehlgeschlagen"
                    }
                }
            }
        }

        fun create(context: Context): AppDatabase =
            // Dateiname bleibt trotz Umbenennung in "MyFeed" unverändert, damit
            // bestehende Installationen ihre Feeds und Artikel behalten.
            Room.databaseBuilder(context, AppDatabase::class.java, "kvaesitso-rss.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
    }
}
