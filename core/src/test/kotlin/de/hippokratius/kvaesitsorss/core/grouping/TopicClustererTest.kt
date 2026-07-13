package de.hippokratius.kvaesitsorss.core.grouping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicClustererTest {

    private val clusterer = TopicClusterer()

    private val now = 1_780_000_000_000L
    private fun hoursAgo(h: Int) = now - h * 60L * 60 * 1000

    @Test
    fun `groups german headlines about the same topic`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung beschließt neues Klimapaket", hoursAgo(1)),
                ClusterCandidate(2, "Klimapaket: Bundesregierung einigt sich auf Maßnahmen", hoursAgo(3)),
                ClusterCandidate(3, "Bayern München gewinnt das Pokalfinale", hoursAgo(2)),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.single())
    }

    @Test
    fun `groups english headlines about the same topic`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Apple unveils new iPhone with smarter camera", hoursAgo(2)),
                ClusterCandidate(2, "New iPhone announced: Apple bets on cameras", hoursAgo(5)),
                ClusterCandidate(3, "Stock markets rally after rate decision", hoursAgo(1)),
            ),
        )

        assertEquals(1, groups.size)
        assertTrue(groups.single().containsAll(listOf(1L, 2L)))
    }

    @Test
    fun `does not group unrelated headlines`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung beschließt neues Klimapaket", hoursAgo(1)),
                ClusterCandidate(2, "Bayern München gewinnt das Pokalfinale", hoursAgo(2)),
                ClusterCandidate(3, "Neuer Chip von TSMC vorgestellt", hoursAgo(3)),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `respects the time window`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung beschließt neues Klimapaket", hoursAgo(0)),
                ClusterCandidate(2, "Klimapaket: Bundesregierung einigt sich auf Maßnahmen", hoursAgo(72)),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `merges transitively via union find`() {
        // 1 und 2 teilen Klimapaket/Bundesregierung, 2 und 3 teilen Klimapaket/Maßnahmen.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung beschließt Klimapaket", hoursAgo(1)),
                ClusterCandidate(2, "Klimapaket der Bundesregierung: Maßnahmen im Überblick", hoursAgo(10)),
                ClusterCandidate(3, "Kritik an Maßnahmen im Klimapaket", hoursAgo(20)),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().size)
    }

    @Test
    fun `tolerates word forms via prefix matching`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Warnstreik legt Flughäfen lahm", hoursAgo(1)),
                ClusterCandidate(2, "Warnstreiks an Flughäfen: Tausende Flüge fallen aus", hoursAgo(4)),
            ),
        )
        assertEquals(1, groups.size)
    }

    @Test
    fun `groups are sorted newest first`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Apple unveils new iPhone with smarter camera", hoursAgo(20)),
                ClusterCandidate(2, "New iPhone announced: Apple bets on cameras", hoursAgo(2)),
                ClusterCandidate(3, "Warnstreik legt Flughäfen lahm", hoursAgo(1)),
                ClusterCandidate(4, "Warnstreiks an Flughäfen: Tausende Flüge fallen aus", hoursAgo(4)),
            ),
        )

        assertEquals(2, groups.size)
        // Gruppe mit dem neuesten Artikel (id=3) zuerst, innerhalb der Gruppe neueste zuerst.
        assertEquals(listOf(3L, 4L), groups[0])
        assertEquals(listOf(2L, 1L), groups[1])
    }

    @Test
    fun `handles empty and single input`() {
        assertTrue(clusterer.cluster(emptyList()).isEmpty())
        assertTrue(clusterer.cluster(listOf(ClusterCandidate(1, "Nur einer", now))).isEmpty())
    }

    @Test
    fun `groups cross-source headlines with two salient shared tokens below strict overlap`() {
        // A/B teilen {klimapaket, streit} → Score 2/5 = 0.4 (strikt: zu wenig).
        // Die Füller heben die Dokumentfrequenz beider Tokens auf 5, sodass nur
        // die Salient-Regel (nicht die Rare-Single-Regel) greifen kann.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung einigt sich nach langem Streit auf Klimapaket", hoursAgo(1), "tagesschau"),
                ClusterCandidate(2, "Klimapaket kommt: Koalition beendet Streit über Finanzierung der Maßnahmen", hoursAgo(3), "spiegel"),
                ClusterCandidate(3, "Klimapaket stößt bei Wirtschaft auf Skepsis", hoursAgo(2), "f1"),
                ClusterCandidate(4, "Kommentar: Warum das Klimapaket enttäuscht", hoursAgo(4), "f2"),
                ClusterCandidate(5, "Klimapaket im Faktencheck", hoursAgo(5), "f3"),
                ClusterCandidate(6, "Streit um Erbschaft eskaliert vor Gericht", hoursAgo(6), "f4"),
                ClusterCandidate(7, "Tarifverhandlungen: Streit dauert an", hoursAgo(7), "f5"),
                ClusterCandidate(8, "Streit im Stadtrat um Radwege", hoursAgo(8), "f6"),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.single())
    }

    @Test
    fun `groups cross-source headlines sharing a single rare token`() {
        // Nur "preiserhöhung" ist geteilt ("bahn"/"bahnfahren" liegt unter dem
        // Präfix-Minimum) – quellenübergreifend reicht das bei seltenem Token.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bahn kündigt massive Preiserhöhung zum Fahrplanwechsel an", hoursAgo(1), "heise"),
                ClusterCandidate(2, "Bahnfahren wird teurer: Preiserhöhung im Dezember beschlossen", hoursAgo(4), "zeit"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.single())
    }

    @Test
    fun `relaxed rules do not apply within the same source`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bahn kündigt massive Preiserhöhung zum Fahrplanwechsel an", hoursAgo(1), "heise"),
                ClusterCandidate(2, "Bahnfahren wird teurer: Preiserhöhung im Dezember beschlossen", hoursAgo(4), "heise"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `relaxed rules do not apply without source information`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bahn kündigt massive Preiserhöhung zum Fahrplanwechsel an", hoursAgo(1)),
                ClusterCandidate(2, "Bahnfahren wird teurer: Preiserhöhung im Dezember beschlossen", hoursAgo(4)),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `frequent shared token does not chain articles into a mega group`() {
        // "trump" steckt in allen 8 Titeln (Dokumentfrequenz 8 > Rare-Schwelle);
        // ein einzelnes häufiges Wort darf keine Gruppe verketten.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Trump kündigt neue Zölle gegen Mexiko an", hoursAgo(1), "s1"),
                ClusterCandidate(2, "Trump besucht Automesse in Detroit", hoursAgo(2), "s1"),
                ClusterCandidate(3, "Trump droht Notenbank mit Umbau", hoursAgo(3), "s2"),
                ClusterCandidate(4, "Golfturnier: Trump eröffnet Anlage in Schottland", hoursAgo(4), "s2"),
                ClusterCandidate(5, "Trump empfängt Staatschefs zum Gipfel", hoursAgo(5), "s3"),
                ClusterCandidate(6, "Umfrage sieht Trump im Aufwind", hoursAgo(6), "s3"),
                ClusterCandidate(7, "Trump verschiebt Entscheidung zu Handelsabkommen", hoursAgo(7), "s4"),
                ClusterCandidate(8, "Buch über Trump sorgt für Wirbel", hoursAgo(8), "s4"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `per source cap keeps ticker floods from inflating document frequency`() {
        // "kirchentag" kommt roh in 5 Titeln vor; gedeckelt (max. 2 je Quelle)
        // sind es 3 – der fremde Artikel gruppiert daher mit den Tickern.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Kirchentag startet mit Eröffnungsgottesdienst", hoursAgo(1), "a"),
                ClusterCandidate(2, "Kirchentag: Zehntausende Besucher erwartet", hoursAgo(2), "a"),
                ClusterCandidate(3, "Kirchentag diskutiert über Friedensethik", hoursAgo(3), "a"),
                ClusterCandidate(4, "Kirchentag endet mit großem Abschlussgottesdienst", hoursAgo(4), "a"),
                ClusterCandidate(5, "Evangelischer Kirchentag zieht positive Bilanz", hoursAgo(5), "b"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(5, groups.single().size)
    }

    @Test
    fun `relaxed rules respect their rarity gates`() {
        val strictOnly = TopicClusterer(crossFeedSalientMaxDf = 1, crossFeedRareMaxDf = 0)
        val groups = strictOnly.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung einigt sich nach langem Streit auf Klimapaket", hoursAgo(1), "tagesschau"),
                ClusterCandidate(2, "Klimapaket kommt: Koalition beendet Streit über Finanzierung der Maßnahmen", hoursAgo(3), "spiegel"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `tokenizer removes stopwords and short tokens`() {
        val tokens = TopicClusterer.tokenize("Die Bundesregierung hat ein neues Klimapaket beschlossen – 20 Jahre danach")
        assertTrue("bundesregierung" in tokens)
        assertTrue("klimapaket" in tokens)
        assertTrue("die" !in tokens)
        assertTrue("hat" !in tokens)
        assertTrue("20" !in tokens)
    }
}
