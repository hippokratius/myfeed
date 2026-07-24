package de.hippokratius.myfeed.core.nextcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewsSyncLogicTest {

    private fun item(
        id: Long = 1,
        unread: Boolean = true,
        starred: Boolean = false,
        lastModified: Long = 1_700_000_000,
    ) = NewsItem(id = id, feedId = 1, unread = unread, starred = starred, lastModified = lastModified)

    private val clean = LocalStatus(
        readAt = null,
        bookmarkedAt = null,
        pendingReadSync = false,
        pendingStarSync = false,
    )

    @Test
    fun `cursor advances to max lastModified and never goes backwards`() {
        assertEquals(200, NewsSyncLogic.nextCursor(100, listOf(item(lastModified = 150), item(lastModified = 200))))
        assertEquals(300, NewsSyncLogic.nextCursor(300, listOf(item(lastModified = 200))))
        assertEquals(100, NewsSyncLogic.nextCursor(100, emptyList()))
    }

    @Test
    fun `server read state applies when no pending flag`() {
        val merged = NewsSyncLogic.mergedStatus(clean, item(unread = false, lastModified = 1000))
        assertEquals(1_000_000, merged.readAt)

        // Server sagt ungelesen -> lokaler readAt wird zurückgenommen.
        val reverted = NewsSyncLogic.mergedStatus(clean.copy(readAt = 5), item(unread = true))
        assertNull(reverted.readAt)
    }

    @Test
    fun `pending flags win over server state`() {
        val pendingRead = clean.copy(readAt = 42, pendingReadSync = true)
        assertEquals(42, NewsSyncLogic.mergedStatus(pendingRead, item(unread = true)).readAt)

        // Lokal entsternt (Push steht aus) -> Server-Stern setzt sich nicht durch.
        val pendingUnstar = clean.copy(bookmarkedAt = null, pendingStarSync = true)
        assertNull(NewsSyncLogic.mergedStatus(pendingUnstar, item(starred = true)).bookmarkedAt)
    }

    @Test
    fun `existing bookmark timestamp stays stable, re-star restarts it`() {
        // Stern bleibt Stern: Frist läuft weiter (Zeitstempel unverändert).
        val kept = NewsSyncLogic.mergedStatus(
            clean.copy(bookmarkedAt = 111),
            item(starred = true, lastModified = 999),
        )
        assertEquals(111, kept.bookmarkedAt)

        // Lokal kein Stern (abgelaufen/entfernt), Server hat wieder gesternt:
        // Frist startet mit Server-Zeitstempel neu.
        val restarted = NewsSyncLogic.mergedStatus(clean, item(starred = true, lastModified = 999))
        assertEquals(999_000, restarted.bookmarkedAt)

        // Server hat entsternt -> Lesezeichen verschwindet.
        assertNull(
            NewsSyncLogic.mergedStatus(clean.copy(bookmarkedAt = 111), item(starred = false)).bookmarkedAt,
        )
    }

    @Test
    fun `merge is idempotent for cursor overlap replays`() {
        val once = NewsSyncLogic.mergedStatus(clean, item(unread = false, starred = true, lastModified = 1000))
        val twice = NewsSyncLogic.mergedStatus(once, item(unread = false, starred = true, lastModified = 1000))
        assertEquals(once, twice)
    }

    @Test
    fun `chunking splits ids and drops nothing`() {
        assertEquals(emptyList(), NewsSyncLogic.chunkIds(emptyList()))
        val ids = (1L..1201L).toList()
        val chunks = NewsSyncLogic.chunkIds(ids)
        assertEquals(3, chunks.size)
        assertEquals(ids, chunks.flatten())
    }
}
