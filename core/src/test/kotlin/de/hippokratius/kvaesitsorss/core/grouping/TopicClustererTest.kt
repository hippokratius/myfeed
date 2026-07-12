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
    fun `tokenizer removes stopwords and short tokens`() {
        val tokens = TopicClusterer.tokenize("Die Bundesregierung hat ein neues Klimapaket beschlossen – 20 Jahre danach")
        assertTrue("bundesregierung" in tokens)
        assertTrue("klimapaket" in tokens)
        assertTrue("die" !in tokens)
        assertTrue("hat" !in tokens)
        assertTrue("20" !in tokens)
    }
}
