package de.hippokratius.myfeed.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Herkunft eines Feeds/Artikels: lokal abonniertes RSS oder Spiegel einer
 * Nextcloud-News-Instanz. Beide Bestände liegen in denselben Tabellen und
 * werden über diese Spalte getrennt – der Reader zeigt nur das aktive Origin.
 */
object Origin {
    const val LOCAL = "LOCAL"
    const val NEXTCLOUD = "NEXTCLOUD"
}

@Entity(
    tableName = "feeds",
    indices = [
        // Derselbe Feed darf lokal UND als Nextcloud-Spiegel existieren.
        Index(value = ["origin", "url"], unique = true),
        Index(value = ["origin", "remoteId"], unique = true),
        Index(value = ["category"]),
    ],
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    /** Vom Feed deklariertes Logo (RSS channel/image, Atom icon, NC-Favicon). */
    val iconUrl: String? = null,
    /** Pfad zum lokal gecachten, verkleinerten Logo. */
    val iconPath: String? = null,
    /** Kategorie/Thema als Anzeigename, null = ohne Kategorie (NC: Ordnername). */
    val category: String? = null,
    @ColumnInfo(defaultValue = "LOCAL")
    val origin: String = Origin.LOCAL,
    /** Feed-ID der News-API, null bei lokalen Feeds. */
    val remoteId: Long? = null,
)

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["origin", "publishedAt"]),
        Index(value = ["remoteId"], unique = true),
        Index(value = ["groupId"]),
        Index(value = ["archivedAt"]),
        Index(value = ["bookmarkedAt"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    /** Name des Feeds, denormalisiert für Widget-Anzeige ohne Join. */
    val sourceTitle: String,
    val guid: String,
    val title: String,
    val link: String,
    val imageUrl: String?,
    /** Pfad zum lokal gecachten, verkleinerten Thumbnail. */
    val thumbPath: String?,
    val publishedAt: Long,
    val fetchedAt: Long,
    /** Themen-Gruppe (null = Einzelartikel). Wird bei jedem Sync neu berechnet. */
    val groupId: String?,
    /** Wann der Artikel im Reader vorbeigescrollt wurde (null = ungelesen). */
    val readAt: Long? = null,
    /** Wann der Artikel geöffnet wurde – geöffnete Artikel landen im Archiv (null = nie). */
    val archivedAt: Long? = null,
    /** Wann das Lesezeichen gesetzt wurde (null = kein Lesezeichen). */
    val bookmarkedAt: Long? = null,
    @ColumnInfo(defaultValue = "LOCAL")
    val origin: String = Origin.LOCAL,
    /** Item-ID der News-API, null bei lokalen Artikeln. */
    val remoteId: Long? = null,
    /** Gelesen-Status geändert, aber noch nicht zum Server gepusht. */
    @ColumnInfo(defaultValue = "0")
    val pendingReadSync: Boolean = false,
    /** Stern-Status geändert, aber noch nicht zum Server gepusht. */
    @ColumnInfo(defaultValue = "0")
    val pendingStarSync: Boolean = false,
) {
    val isRead: Boolean get() = readAt != null
    val isBookmarked: Boolean get() = bookmarkedAt != null
}
