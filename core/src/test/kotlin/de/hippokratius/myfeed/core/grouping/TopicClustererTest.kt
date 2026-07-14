package de.hippokratius.myfeed.core.grouping

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
    fun `a single shared token is never enough to group`() {
        // Nur "preiserhöhung" ist geteilt ("bahn"/"bahnfahren" liegt unter dem
        // Präfix-Minimum). Ein einzelnes gemeinsames Wort würde über Union-Find
        // zu Riesengruppen verketten und reicht daher auch quellenübergreifend nie.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bahn kündigt massive Preiserhöhung zum Fahrplanwechsel an", hoursAgo(1), "heise"),
                ClusterCandidate(2, "Bahnfahren wird teurer: Preiserhöhung im Dezember beschlossen", hoursAgo(4), "zeit"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `relaxed rule does not apply within the same source`() {
        // Gleiches Paar wie im Cross-Source-Positivtest (Score 0.4), aber aus
        // einer Quelle: nur die strikte Regel zählt.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung einigt sich nach langem Streit auf Klimapaket", hoursAgo(1), "tagesschau"),
                ClusterCandidate(2, "Klimapaket kommt: Koalition beendet Streit über Finanzierung der Maßnahmen", hoursAgo(3), "tagesschau"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `relaxed rule does not apply without source information`() {
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Bundesregierung einigt sich nach langem Streit auf Klimapaket", hoursAgo(1)),
                ClusterCandidate(2, "Klimapaket kommt: Koalition beendet Streit über Finanzierung der Maßnahmen", hoursAgo(3)),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `single shared tokens do not chain articles into a mega group`() {
        // Jedes Paar teilt höchstens ein Wort – so entsteht keine Gruppe,
        // egal wie viele Quellen beteiligt sind.
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
    fun `mixed batch with overlapping single tokens stays ungrouped`() {
        // Realistisches Fehlerbild: viele Titel weniger Quellen, die sich
        // paarweise genau ein Allerweltswort teilen (polizei, demonstration,
        // berlin, preise) – daraus darf keine Gruppe entstehen.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Polizei nimmt Verdächtigen nach Überfall fest", hoursAgo(1), "s1"),
                ClusterCandidate(2, "Wien: Polizei verstärkt Präsenz bei Demonstration", hoursAgo(2), "s2"),
                ClusterCandidate(3, "Demonstration gegen Mietkosten in Berlin", hoursAgo(3), "s3"),
                ClusterCandidate(4, "Berlin diskutiert Verbot von E-Scootern", hoursAgo(4), "s1"),
                ClusterCandidate(5, "Streaming-Dienst erhöht Preise erneut", hoursAgo(5), "s2"),
                ClusterCandidate(6, "Kino-Preise steigen: Besucherzahlen sinken", hoursAgo(6), "s3"),
            ),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `relaxed unions stop growing at the cross feed group size limit`() {
        // Kette T1–T8: benachbarte Titel teilen je 2 seltene Wörter (Score 0.4,
        // also nur über die gelockerte Regel verbunden). Ohne Schranke ergäbe
        // das eine 8er-Gruppe; die Schranke (6) kappt die Kette.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "solarpark genehmigung anwohner gemeinderat bebauungsplan", hoursAgo(1), "s1"),
                ClusterCandidate(2, "solarpark genehmigung hafenausbau containerterminal spatenstich", hoursAgo(2), "s2"),
                ClusterCandidate(3, "hafenausbau containerterminal stellenabbau autozulieferer krisengipfel", hoursAgo(3), "s3"),
                ClusterCandidate(4, "stellenabbau autozulieferer impfkampagne grippewelle hausärzte", hoursAgo(4), "s4"),
                ClusterCandidate(5, "impfkampagne grippewelle mietpreisbremse mietendeckel wohnungsmarkt", hoursAgo(5), "s5"),
                ClusterCandidate(6, "mietpreisbremse mietendeckel glasfaser fördermittel breitbandnetz", hoursAgo(6), "s6"),
                ClusterCandidate(7, "glasfaser fördermittel wolfsrudel weidetiere abschussquote", hoursAgo(7), "s7"),
                ClusterCandidate(8, "wolfsrudel weidetiere schafherde almwirtschaft herdenschutz", hoursAgo(8), "s8"),
            ),
        )

        assertTrue(groups.isNotEmpty())
        assertTrue(groups.all { it.size <= 6 }, "keine Gruppe darf die Schranke überschreiten: $groups")
    }

    @Test
    fun `ticker flood does not block cross-source grouping`() {
        // "kirchentag" kommt in 5 Titeln vor (nicht mehr selten genug allein),
        // aber das zweite gemeinsame Wort "friedensethik" ist selten – der
        // fremde Artikel gruppiert mit dem passenden Ticker.
        val groups = clusterer.cluster(
            listOf(
                ClusterCandidate(1, "Kirchentag startet mit Eröffnungsgottesdienst", hoursAgo(1), "a"),
                ClusterCandidate(2, "Kirchentag: Zehntausende Besucher erwartet", hoursAgo(2), "a"),
                ClusterCandidate(3, "Kirchentag diskutiert Friedensethik zwischen Pazifismus und Verantwortung", hoursAgo(3), "a"),
                ClusterCandidate(4, "Kirchentag endet mit großem Abschlussgottesdienst", hoursAgo(4), "a"),
                ClusterCandidate(5, "Streitpunkt Friedensethik: kontroverse Debatten beim Kirchentag", hoursAgo(5), "b"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(3L, 5L), groups.single())
    }

    @Test
    fun `relaxed rule respects its rarity gate`() {
        val strictSalience = TopicClusterer(crossFeedSalientMaxDf = 1)
        val groups = strictSalience.cluster(
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
