package de.hippokratius.myfeed.core.nextcloud

import de.hippokratius.myfeed.core.Beta
import kotlinx.serialization.Serializable

/**
 * DTOs der Nextcloud-News-API v1.3. Alle Felder außer den IDs sind optional
 * mit Defaults, damit fehlende oder zusätzliche Felder älterer/neuerer
 * Server-Versionen das Parsen nicht brechen (ignoreUnknownKeys s. [NewsJson]).
 *
 * Beta: Teil der Nextcloud-News-Anbindung – kann sich noch ändern.
 */
@Beta
@Serializable
data class NewsFolder(
    val id: Long,
    val name: String = "",
)

@Beta
@Serializable
data class NewsFeed(
    val id: Long,
    val url: String? = null,
    val title: String? = null,
    val faviconLink: String? = null,
    /** null oder 0 = kein Ordner (Wurzel). */
    val folderId: Long? = null,
    val link: String? = null,
    val unreadCount: Long = 0,
    val pinned: Boolean = false,
    val updateErrorCount: Long = 0,
    val lastUpdateError: String? = null,
)

@Beta
@Serializable
data class NewsItem(
    val id: Long,
    val feedId: Long,
    val guid: String? = null,
    val guidHash: String? = null,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
    /** Veröffentlichung in Epoch-Sekunden. */
    val pubDate: Long? = null,
    /** Artikelinhalt als HTML – wird nur zur Bild-Extraktion genutzt. */
    val body: String? = null,
    val enclosureMime: String? = null,
    val enclosureLink: String? = null,
    val mediaThumbnail: String? = null,
    val mediaDescription: String? = null,
    val unread: Boolean = false,
    val starred: Boolean = false,
    val rtl: Boolean = false,
    /** Letzte Status-Änderung in Epoch-Sekunden – Basis des Sync-Cursors. */
    val lastModified: Long = 0,
    val fingerprint: String? = null,
)

@Beta
@Serializable
data class NewsFoldersResponse(val folders: List<NewsFolder> = emptyList())

@Beta
@Serializable
data class NewsFeedsResponse(
    val feeds: List<NewsFeed> = emptyList(),
    val starredCount: Long = 0,
    val newestItemId: Long? = null,
)

@Beta
@Serializable
data class NewsItemsResponse(val items: List<NewsItem> = emptyList())

@Beta
@Serializable
data class NewsVersion(val version: String = "")
