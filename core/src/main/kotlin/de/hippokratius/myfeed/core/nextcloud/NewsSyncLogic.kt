package de.hippokratius.myfeed.core.nextcloud

/** Lokaler Lese-/Stern-Zustand eines gespiegelten Artikels (Auszug aus der DB-Zeile). */
data class LocalStatus(
    val readAt: Long?,
    val bookmarkedAt: Long?,
    val pendingReadSync: Boolean,
    val pendingStarSync: Boolean,
)

/** Reine Sync-Regeln: Cursor-Fortschreibung und Status-Merge beim Pull. */
object NewsSyncLogic {

    /**
     * Nächster lastModified-Cursor: Maximum aus bisherigem Cursor und den
     * Zeitstempeln der gelieferten Items. Bewusst OHNE "+1": /items/updated
     * liefert >= Cursor (inklusiv); die garantierte Überlappung ist harmlos,
     * weil der Upsert über remoteId idempotent ist – "+1" könnte dagegen
     * sekundengleiche Items verlieren.
     */
    fun nextCursor(current: Long, items: List<NewsItem>): Long =
        maxOf(current, items.maxOfOrNull { it.lastModified } ?: current)

    /**
     * Merge des Server-Status in den lokalen Status: Ein gesetztes Pending-Flag
     * gewinnt immer (die lokale Änderung ist noch nicht gepusht); sonst ist der
     * Server die Quelle der Wahrheit. Vorhandene Zeitstempel bleiben stabil,
     * damit Fristen (Lesezeichen-Aufbewahrung) nicht ungewollt neu starten –
     * außer der Server meldet eine echte Änderung.
     */
    fun mergedStatus(local: LocalStatus, item: NewsItem): LocalStatus {
        val serverStamp = item.lastModified * 1000

        val readAt = when {
            local.pendingReadSync -> local.readAt
            item.unread -> null
            else -> local.readAt ?: serverStamp
        }

        val bookmarkedAt = when {
            local.pendingStarSync -> local.bookmarkedAt
            !item.starred -> null
            // Erneuter Stern nach lokalem Ablauf oder von anderem Client:
            // Zeitstempel des Servers übernehmen, sonst bestehenden behalten.
            local.bookmarkedAt == null -> serverStamp
            else -> local.bookmarkedAt
        }

        return local.copy(readAt = readAt, bookmarkedAt = bookmarkedAt)
    }

    /** Für den Push: IDs in API-verträgliche Blöcke teilen. */
    fun chunkIds(ids: List<Long>, chunkSize: Int = PUSH_CHUNK_SIZE): List<List<Long>> =
        if (ids.isEmpty()) emptyList() else ids.chunked(chunkSize)

    const val PUSH_CHUNK_SIZE = 500
}
