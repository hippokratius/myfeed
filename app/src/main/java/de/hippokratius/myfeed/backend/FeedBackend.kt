package de.hippokratius.myfeed.backend

import de.hippokratius.myfeed.core.model.OpmlFeed
import de.hippokratius.myfeed.data.FeedEntity

/** Aktives Backend: lokal abonnierte RSS-Feeds oder eine Nextcloud-News-Instanz. */
enum class BackendMode { LOCAL_RSS, NEXTCLOUD_NEWS }

data class BackendCapabilities(
    /** Feed-Verwaltung (Anlegen/Löschen/Kategorie) braucht eine Server-Verbindung. */
    val feedManagementNeedsNetwork: Boolean,
)

/** Ergebnis eines OPML-Imports; failed > 0 kommt nur im Nextcloud-Modus vor. */
data class ImportResult(val added: Int, val failed: Int)

sealed class BackendException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Kein Nextcloud-Konto verbunden (oder Verbindung wurde getrennt). */
    class NotConnected : BackendException("Kein Nextcloud-Konto verbunden")

    /** Konto entzogen / Token ungültig – Nutzer muss sich neu anmelden. */
    class AuthLost(cause: Throwable) : BackendException("Nextcloud-Zugriff verloren", cause)

    /** Die News-App ist auf dem Server nicht installiert (404 auf /version). */
    class NewsAppMissing : BackendException("Nextcloud-News-App fehlt auf dem Server")

    /** Server nicht erreichbar / Netzwerkfehler. */
    class ServerUnreachable(cause: Throwable) :
        BackendException("Nextcloud-Server nicht erreichbar", cause)

    /** Der Server hat den Feed abgelehnt (existiert schon, ungültig, …). */
    class FeedRejected(val httpCode: Int) :
        BackendException("Feed vom Server abgelehnt (HTTP $httpCode)")
}

/**
 * Schmale Naht zwischen UI/Worker und der jeweiligen Datenquelle. Lesende
 * Flows bleiben auf den DAOs (origin-gefiltert); hier laufen nur der Sync und
 * alle schreibenden Operationen zusammen.
 */
interface FeedBackend {
    val mode: BackendMode
    val capabilities: BackendCapabilities

    /** Vollständiger Sync inkl. gemeinsamer Nachverarbeitung (Retention, Gruppen, Widget). */
    suspend fun syncAll()

    suspend fun addFeed(url: String, title: String?, category: String?)

    suspend fun deleteFeed(feed: FeedEntity)

    suspend fun setCategory(feed: FeedEntity, category: String?)

    suspend fun importOpml(feeds: List<OpmlFeed>): ImportResult

    /** Scroll-Tracking: Artikel als gelesen markieren. */
    suspend fun markRead(articleIds: List<Long>, readAt: Long)

    /** Artikel wurde geöffnet: archivieren (Nextcloud: zusätzlich Gelesen-Push, E6). */
    suspend fun markOpened(articleId: Long, timestamp: Long)

    suspend fun setBookmarked(articleId: Long, bookmarkedAt: Long?)
}
