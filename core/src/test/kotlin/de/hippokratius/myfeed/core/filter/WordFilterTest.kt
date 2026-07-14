package de.hippokratius.myfeed.core.filter

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordFilterTest {

    @Test
    fun `matches word as part of a compound`() {
        val filter = WordFilter(listOf("Fußball"))

        assertTrue(filter.matches("Fußballspieler wechselt den Verein"))
        assertTrue(filter.matches("Bundesliga: Spannung im Frauenfußball"))
    }

    @Test
    fun `matches case insensitively including umlauts`() {
        val filter = WordFilter(listOf("KLIMASCHUTZ", "börse"))

        assertTrue(filter.matches("Neues Klimaschutzgesetz beschlossen"))
        assertTrue(filter.matches("BÖRSENCRASH befürchtet"))
    }

    @Test
    fun `matches if any of multiple words is contained`() {
        val filter = WordFilter(listOf("Krieg", "Wahl"))

        assertTrue(filter.matches("Landtagswahl in Bayern"))
        assertTrue(filter.matches("Kriegsende gefordert"))
        assertFalse(filter.matches("Rezept der Woche: Kürbissuppe"))
    }

    @Test
    fun `does not match unrelated titles`() {
        val filter = WordFilter(listOf("Fußball"))

        assertFalse(filter.matches("Handballerinnen gewinnen EM-Titel"))
    }

    @Test
    fun `blank and empty words never match`() {
        val filter = WordFilter(listOf("", "   "))

        assertTrue(filter.isEmpty)
        assertFalse(filter.matches("Beliebiger Titel"))
    }

    @Test
    fun `trims words before matching`() {
        val filter = WordFilter(listOf("  Wahl  "))

        assertTrue(filter.matches("Wahlkampf beginnt"))
        assertFalse(filter.isEmpty)
    }

    @Test
    fun `matching is independent of the default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val filter = WordFilter(listOf("IPHONE"))

            assertTrue(filter.matches("Neues iPhone vorgestellt"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
