package de.hippokratius.kvaesitsorss.core.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedDatesTest {

    @Test
    fun `parses rfc1123 with offset`() {
        val millis = FeedDates.parseToEpochMillis("Fri, 10 Jul 2026 08:30:00 +0200")
        assertNotNull(millis)
    }

    @Test
    fun `parses rfc1123 with gmt and single digit day`() {
        assertNotNull(FeedDates.parseToEpochMillis("Mon, 6 Jul 2026 10:15:30 GMT"))
    }

    @Test
    fun `parses legacy timezone names`() {
        assertNotNull(FeedDates.parseToEpochMillis("Fri, 10 Jul 2026 08:30:00 EST"))
    }

    @Test
    fun `parses iso formats`() {
        assertNotNull(FeedDates.parseToEpochMillis("2026-07-10T06:00:00Z"))
        assertNotNull(FeedDates.parseToEpochMillis("2026-07-10T06:00:00+02:00"))
        assertNotNull(FeedDates.parseToEpochMillis("2026-07-10"))
    }

    @Test
    fun `iso date equals expected epoch`() {
        assertEquals(1783749600000L, FeedDates.parseToEpochMillis("2026-07-11T06:00:00Z"))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(FeedDates.parseToEpochMillis("irgendwann"))
        assertNull(FeedDates.parseToEpochMillis(""))
        assertNull(FeedDates.parseToEpochMillis(null))
    }
}
