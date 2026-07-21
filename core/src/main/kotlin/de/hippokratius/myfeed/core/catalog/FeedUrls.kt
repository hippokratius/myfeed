package de.hippokratius.myfeed.core.catalog

/** URL-Vergleichslogik für den "schon hinzugefügt"-Abgleich im Katalog. */
object FeedUrls {

    /**
     * Kanonische Form für Gleichheitsvergleiche: Scheme entfernt (http ≙ https),
     * Host lowercase ohne führendes "www.", Pfad ohne Trailing-Slash.
     * Query-Parameter bleiben erhalten (unterscheiden z. B. Golem-Feeds).
     */
    fun canonical(url: String): String = normalize(url, stripWww = true)

    /**
     * Schlüssel gegen doppelte Netzwerk-Abrufe: wie [canonical] (http ≙ https,
     * Trailing-Slash egal), aber "www." bleibt erhalten. So gelten
     * `example.org/feed` und `www.example.org/feed` als verschiedene Endpunkte
     * und werden beide geladen — nötig, damit die Feed-Suche neben der nackten
     * auch die www.-Variante wirklich probiert.
     */
    fun requestKey(url: String): String = normalize(url, stripWww = false)

    private fun normalize(url: String, stripWww: Boolean): String {
        var rest = url.trim()
        val schemeEnd = rest.indexOf("://")
        if (schemeEnd != -1) rest = rest.substring(schemeEnd + 3)

        val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        var host = if (pathStart == -1) rest else rest.substring(0, pathStart)
        var path = if (pathStart == -1) "" else rest.substring(pathStart)
        host = host.lowercase()
        if (stripWww) host = host.removePrefix("www.")

        // Trailing-Slash nur am Pfadende entfernen, nicht in der Query.
        val queryStart = path.indexOfFirst { it == '?' || it == '#' }
        path = if (queryStart == -1) {
            path.trimEnd('/')
        } else {
            path.substring(0, queryStart).trimEnd('/') + path.substring(queryStart)
        }

        return "$host$path"
    }
}
