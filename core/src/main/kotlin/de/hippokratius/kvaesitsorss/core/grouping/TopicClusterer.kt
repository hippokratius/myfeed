package de.hippokratius.kvaesitsorss.core.grouping

/** Eingabe für die Themen-Gruppierung. */
data class ClusterCandidate(
    val id: Long,
    val title: String,
    val timestampMillis: Long,
    /** Quelle des Artikels (z. B. Feed-ID); null = unbekannt → nur strikte Regel. */
    val sourceKey: String? = null,
)

/**
 * Gruppiert Artikel, die vermutlich über dasselbe Thema berichten – rein
 * heuristisch über gemeinsame Schlüsselwörter in den Überschriften
 * (Smart-Launcher-artiges Verhalten, ohne ML):
 *
 * 1. Titel werden tokenisiert (Kleinschreibung, Stoppwörter raus, kurze Tokens raus).
 * 2. Zwei Artikel gelten als verwandt, wenn sie innerhalb von [windowMillis]
 *    erschienen sind und eine dieser Regeln erfüllen:
 *    - Strikt (jedes Paar): mindestens [minSharedTokens] gemeinsame Schlüsselwörter
 *      und Overlap-Koeffizient (|A∩B| / min(|A|,|B|)) mindestens [minScore].
 *    - Gelockert (nur Paare aus *verschiedenen* Quellen, weil verschiedene
 *      Redaktionen dieselbe Story unterschiedlich formulieren): mindestens
 *      [minSharedTokens] gemeinsame Wörter ab Overlap [crossFeedMinScore], sofern
 *      mindestens ein gemeinsames Wort im Batch selten ist (Dokumentfrequenz
 *      ≤ [crossFeedSalientMaxDf]); oder ein einzelnes gemeinsames, sehr seltenes
 *      Wort (Dokumentfrequenz ≤ [crossFeedRareMaxDf]). Die Dokumentfrequenz zählt
 *      pro Quelle höchstens [PER_SOURCE_DF_CAP] Titel, damit Ticker-Fluten einer
 *      Quelle ein Wort nicht künstlich "häufig" machen.
 *    Wortformen werden über einen Präfix-Vergleich toleriert
 *    ("Regierung"/"Regierungen").
 * 3. Verwandtschaft wird per Union-Find transitiv zu Gruppen zusammengefasst.
 */
class TopicClusterer(
    private val minSharedTokens: Int = 2,
    private val minScore: Double = 0.5,
    private val windowMillis: Long = 48L * 60 * 60 * 1000,
    private val crossFeedMinScore: Double = 0.33,
    private val crossFeedSalientMaxDf: Int = 10,
    private val crossFeedRareMaxDf: Int = 4,
) {

    /**
     * @return Gruppen mit mindestens zwei Mitgliedern; jede Gruppe ist nach
     * Zeitstempel absteigend sortiert (neuester Artikel zuerst).
     */
    fun cluster(candidates: List<ClusterCandidate>): List<List<Long>> {
        val n = candidates.size
        if (n < 2) return emptyList()

        val tokens = candidates.map { tokenize(it.title) }
        val df = documentFrequencies(candidates, tokens)
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

                val shared = sharedTokens(tokens[i], tokens[j], df)
                if (shared.count < 1) continue
                val score = shared.count.toDouble() / minOf(tokens[i].size, tokens[j].size)
                if (relates(candidates[i], candidates[j], shared, score)) union(i, j)
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

    private fun relates(
        a: ClusterCandidate,
        b: ClusterCandidate,
        shared: SharedTokens,
        score: Double,
    ): Boolean {
        if (shared.count >= minSharedTokens && score >= minScore) return true

        val crossFeed = a.sourceKey != null && b.sourceKey != null && a.sourceKey != b.sourceKey
        if (!crossFeed) return false

        if (shared.count >= minSharedTokens &&
            score >= crossFeedMinScore &&
            shared.rarestDf <= crossFeedSalientMaxDf
        ) {
            return true
        }
        return shared.rarestDf <= crossFeedRareMaxDf
    }

    /** Ergebnis des Token-Vergleichs zweier Titel. */
    private data class SharedTokens(
        /** Anzahl Tokens aus A mit (präfix-tolerantem) Treffer in B. */
        val count: Int,
        /**
         * Dokumentfrequenz des seltensten gemeinsamen Wortes; pro gematchtem
         * Token-Paar zählt konservativ das häufigere der beiden Tokens
         * (relevant bei Präfix-Treffern wie "haushaltsstreit"/"haushalt").
         */
        val rarestDf: Int,
    )

    /** Vergleicht Tokens; Wortformen werden per Präfix-Vergleich toleriert. */
    private fun sharedTokens(a: Set<String>, b: Set<String>, df: Map<String, Int>): SharedTokens {
        var count = 0
        var rarest = Int.MAX_VALUE
        for (tokenA in a) {
            var matched = false
            for (tokenB in b) {
                val isMatch = tokenA == tokenB || (
                    minOf(tokenA.length, tokenB.length) >= PREFIX_MIN_LENGTH &&
                        (tokenA.startsWith(tokenB) || tokenB.startsWith(tokenA))
                    )
                if (!isMatch) continue
                matched = true
                val pairDf = maxOf(df[tokenA] ?: 0, df[tokenB] ?: 0)
                if (pairDf < rarest) rarest = pairDf
            }
            if (matched) count++
        }
        return SharedTokens(count, rarest)
    }

    /** Dokumentfrequenz je Token, gedeckelt auf [PER_SOURCE_DF_CAP] Titel pro Quelle. */
    private fun documentFrequencies(
        candidates: List<ClusterCandidate>,
        tokens: List<Set<String>>,
    ): Map<String, Int> {
        val perSource = HashMap<String, HashMap<String?, Int>>()
        for (i in candidates.indices) {
            val source = candidates[i].sourceKey
            for (token in tokens[i]) {
                val bySource = perSource.getOrPut(token) { HashMap() }
                bySource[source] = (bySource[source] ?: 0) + 1
            }
        }
        return perSource.mapValues { (_, bySource) ->
            bySource.values.sumOf { minOf(it, PER_SOURCE_DF_CAP) }
        }
    }

    companion object {
        private const val PREFIX_MIN_LENGTH = 5
        private const val MIN_TOKEN_LENGTH = 3
        private const val PER_SOURCE_DF_CAP = 2

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
