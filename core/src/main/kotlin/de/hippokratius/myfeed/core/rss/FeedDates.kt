package de.hippokratius.myfeed.core.rss

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tolerantes Parsen der in Feeds üblichen Datumsformate. */
object FeedDates {

    private val LEGACY_FORMATS = listOf(
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm zzz",
        "d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
    )

    /** @return Epoch-Millis oder null, wenn das Datum nicht geparst werden kann. */
    fun parseToEpochMillis(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        runCatching {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(text).toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        runCatching {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        // Letzte Rettung für Zeitzonen-Namen wie "EST", die java.time nicht akzeptiert.
        for (pattern in LEGACY_FORMATS) {
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.ENGLISH)
                return format.parse(text)!!.time
            }
        }
        return null
    }
}
