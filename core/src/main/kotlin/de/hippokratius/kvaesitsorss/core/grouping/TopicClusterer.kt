package de.hippokratius.kvaesitsorss.core.grouping

/** Eingabe für die Themen-Gruppierung. */
data class ClusterCandidate(
    val id: Long,
    val title: String,
    val timestampMillis: Long,
)

/**
 * Gruppiert Artikel, die vermutlich über dasselbe Thema berichten – rein
 * heuristisch über gemeinsame Schlüsselwörter in den Überschriften
 * (Smart-Launcher-artiges Verhalten, ohne ML):
 *
 * 1. Titel werden tokenisiert (Kleinschreibung, Stoppwörter raus, kurze Tokens raus).
 * 2. Zwei Artikel gelten als verwandt, wenn sie innerhalb von [windowMillis]
 *    erschienen sind, mindestens [minSharedTokens] Schlüsselwörter teilen und der
 *    Overlap-Koeffizient (|A∩B| / min(|A|,|B|)) mindestens [minScore] beträgt.
 *    Wortformen werden über einen Präfix-Vergleich toleriert
 *    ("Regierung"/"Regierungen").
 * 3. Verwandtschaft wird per Union-Find transitiv zu Gruppen zusammengefasst.
 */
class TopicClusterer(
    private val minSharedTokens: Int = 2,
    private val minScore: Double = 0.5,
    private val windowMillis: Long = 48L * 60 * 60 * 1000,
) {

    /**
     * @return Gruppen mit mindestens zwei Mitgliedern; jede Gruppe ist nach
     * Zeitstempel absteigend sortiert (neuester Artikel zuerst).
     */
    fun cluster(candidates: List<ClusterCandidate>): List<List<Long>> {
        val n = candidates.size
        if (n < 2) return emptyList()

        val tokens = candidates.map { tokenize(it.title) }
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (i in 0 until n) {
            if (tokens[i].isEmpty()) continue
            for (j in i + 1 until n) {
                if (tokens[j].isEmpty()) continue
                val timeDiff = candidates[i].timestampMillis - candidates[j].timestampMillis
                if (timeDiff > windowMillis || timeDiff < -windowMillis) continue
                if (find(i) == find(j)) continue

                val shared = sharedTokenCount(tokens[i], tokens[j])
                if (shared < minSharedTokens) continue
                val score = shared.toDouble() / minOf(tokens[i].size, tokens[j].size)
                if (score >= minScore) union(i, j)
            }
        }

        val groups = HashMap<Int, MutableList<ClusterCandidate>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { mutableListOf() } += candidates[i]
        }
        return groups.values
            .filter { it.size >= 2 }
            .map { group -> group.sortedByDescending { it.timestampMillis }.map { it.id } }
            .sortedByDescending { groupIds ->
                candidates.first { it.id == groupIds.first() }.timestampMillis
            }
    }

    /** Zählt gemeinsame Tokens; Wortformen werden per Präfix-Vergleich toleriert. */
    private fun sharedTokenCount(a: Set<String>, b: Set<String>): Int {
        var count = 0
        for (tokenA in a) {
            val matches = b.any { tokenB ->
                tokenA == tokenB || (
                    minOf(tokenA.length, tokenB.length) >= PREFIX_MIN_LENGTH &&
                        (tokenA.startsWith(tokenB) || tokenB.startsWith(tokenA))
                    )
            }
            if (matches) count++
        }
        return count
    }

    companion object {
        private const val PREFIX_MIN_LENGTH = 5
        private const val MIN_TOKEN_LENGTH = 3

        private val SPLIT_REGEX = Regex("[^\\p{L}\\p{Nd}]+")

        private val STOPWORDS = setOf(
            // Deutsch
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem",
            "einer", "eines", "und", "oder", "aber", "auch", "nach", "vor", "bei", "mit",
            "ohne", "für", "fuer", "gegen", "über", "ueber", "unter", "zwischen", "aus",
            "von", "zum", "zur", "ist", "sind", "war", "waren", "wird", "werden", "wurde",
            "wurden", "hat", "haben", "hatte", "hatten", "kann", "können", "koennen",
            "soll", "sollen", "will", "wollen", "muss", "müssen", "muessen", "nicht",
            "kein", "keine", "mehr", "weniger", "als", "wie", "auf", "das", "sich",
            "noch", "schon", "nur", "was", "wer", "wie", "wann", "warum", "wegen",
            "beim", "vom", "seit", "bis", "dass", "weil", "wenn", "trotz", "sowie",
            "etwa", "rund", "viele", "alle", "jetzt", "heute", "morgen", "gestern",
            "sein", "seine", "ihre", "ihr", "ihren", "diese", "dieser", "dieses",
            "neue", "neuer", "neues", "neuen", "immer", "dann", "doch", "sagt",
            // Englisch
            "the", "and", "for", "with", "without", "from", "into", "onto", "over",
            "under", "between", "out", "off", "are", "was", "were", "been", "being",
            "will", "would", "can", "could", "shall", "should", "may", "might", "must",
            "not", "more", "less", "new", "after", "before", "during", "says", "said",
            "say", "how", "why", "what", "who", "when", "where", "this", "that", "these",
            "those", "his", "her", "its", "their", "has", "have", "had", "about",
            "amid", "against", "than", "then", "now", "today", "just", "still", "all",
        )

        fun tokenize(title: String): Set<String> =
            title.lowercase()
                .split(SPLIT_REGEX)
                .asSequence()
                .map { it.trim() }
                .filter { it.length >= MIN_TOKEN_LENGTH }
                .filter { it !in STOPWORDS }
                .toSet()
    }
}
