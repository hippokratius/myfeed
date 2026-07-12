package de.hippokratius.kvaesitsorss.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feeds",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["category"]),
    ],
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val enabled: Boolean = true,
    /** Vom Feed deklariertes Logo (RSS channel/image, Atom icon). */
    val iconUrl: String? = null,
    /** Pfad zum lokal gecachten, verkleinerten Logo. */
    val iconPath: String? = null,
    /** Kategorie/Thema als Anzeigename, null = ohne Kategorie. */
    val category: String? = null,
)

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["groupId"]),
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
)
