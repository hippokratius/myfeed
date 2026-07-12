package de.hippokratius.kvaesitsorss.core.catalog

/** Thema eines Katalog-Feeds; die App mappt auf lokalisierte Anzeigenamen. */
enum class CatalogCategory {
    NEWS,
    REGIONAL,
    TECH,
    SPORT,
    SCIENCE,
    ECONOMY,
    CULTURE,
}

/** Ein kuratierter Feed-Vorschlag. */
data class CatalogFeed(
    val title: String,
    /** Kanonische https-Feed-URL. */
    val url: String,
    val category: CatalogCategory,
    /** ISO-639-1-Sprachcode, aktuell "de" oder "en". */
    val language: String,
)

/**
 * Mitgelieferter Katalog bekannter Feeds für den "Feeds entdecken"-Screen.
 * Bewusst als Kotlin-Liste statt JSON: typsicher, offline, ohne Parser-Code.
 */
object FeedCatalog {

    val feeds: List<CatalogFeed> = listOf(
        // Nachrichten
        CatalogFeed("Tagesschau", "https://www.tagesschau.de/xml/rss2/", CatalogCategory.NEWS, "de"),
        CatalogFeed("DER SPIEGEL", "https://www.spiegel.de/schlagzeilen/index.rss", CatalogCategory.NEWS, "de"),
        CatalogFeed("ZEIT ONLINE", "https://newsfeed.zeit.de/index", CatalogCategory.NEWS, "de"),
        CatalogFeed("Süddeutsche Zeitung", "https://rss.sueddeutsche.de/rss/Topthemen", CatalogCategory.NEWS, "de"),
        CatalogFeed("Deutsche Welle", "https://rss.dw.com/rdf/rss-de-all", CatalogCategory.NEWS, "de"),
        CatalogFeed("KATAPULTU", "https://katapultu-magazin.de/feed/", CatalogCategory.NEWS, "de"),
        CatalogFeed("DER STANDARD", "https://www.derstandard.at/rss", CatalogCategory.NEWS, "de"),
        CatalogFeed("ORF News", "https://rss.orf.at/news.xml", CatalogCategory.NEWS, "de"),
        CatalogFeed("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", CatalogCategory.NEWS, "en"),
        CatalogFeed("The Guardian", "https://www.theguardian.com/world/rss", CatalogCategory.NEWS, "en"),

        // Regional
        CatalogFeed("NDR", "https://www.ndr.de/nachrichten/index-rss.xml", CatalogCategory.REGIONAL, "de"),
        CatalogFeed("MDR", "https://www.mdr.de/nachrichten/nachrichten100-rss.xml", CatalogCategory.REGIONAL, "de"),
        CatalogFeed("WDR", "https://www1.wdr.de/nachrichten/index~rss2.xml", CatalogCategory.REGIONAL, "de"),
        CatalogFeed("hessenschau", "https://www.hessenschau.de/index.rss", CatalogCategory.REGIONAL, "de"),
        CatalogFeed("KATAPULT MV", "https://katapult-mv.de/feed/", CatalogCategory.REGIONAL, "de"),

        // Technik
        CatalogFeed("heise online", "https://www.heise.de/rss/heise-atom.xml", CatalogCategory.TECH, "de"),
        CatalogFeed("Golem.de", "https://rss.golem.de/rss.php?feed=RSS2.0", CatalogCategory.TECH, "de"),
        CatalogFeed("netzpolitik.org", "https://netzpolitik.org/feed/", CatalogCategory.TECH, "de"),
        CatalogFeed("t3n", "https://t3n.de/rss.xml", CatalogCategory.TECH, "de"),
        CatalogFeed("ComputerBase", "https://www.computerbase.de/rss/news.xml", CatalogCategory.TECH, "de"),
        CatalogFeed("Caschys Blog", "https://stadt-bremerhaven.de/feed/", CatalogCategory.TECH, "de"),
        CatalogFeed("The Verge", "https://www.theverge.com/rss/index.xml", CatalogCategory.TECH, "en"),
        CatalogFeed("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", CatalogCategory.TECH, "en"),
        CatalogFeed("Hacker News", "https://news.ycombinator.com/rss", CatalogCategory.TECH, "en"),
        CatalogFeed("TechCrunch", "https://techcrunch.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("WIRED", "https://www.wired.com/feed/rss", CatalogCategory.TECH, "en"),
        CatalogFeed("Engadget", "https://www.engadget.com/rss.xml", CatalogCategory.TECH, "en"),
        CatalogFeed("MIT Technology Review", "https://www.technologyreview.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("The Register", "https://www.theregister.com/headlines.atom", CatalogCategory.TECH, "en"),
        CatalogFeed("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", CatalogCategory.TECH, "en"),

        // Sport
        CatalogFeed("Sportschau", "https://www.sportschau.de/index~rss2.xml", CatalogCategory.SPORT, "de"),
        CatalogFeed("kicker", "https://newsfeed.kicker.de/news/aktuell", CatalogCategory.SPORT, "de"),
        CatalogFeed("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", CatalogCategory.SPORT, "en"),

        // Wissenschaft
        CatalogFeed("Spektrum der Wissenschaft", "https://www.spektrum.de/alias/rss/spektrum-de-rss-feed/996406", CatalogCategory.SCIENCE, "de"),
        CatalogFeed("scinexx", "https://www.scinexx.de/feed/", CatalogCategory.SCIENCE, "de"),
        CatalogFeed("KATAPULT", "https://katapult-magazin.de/rss.xml", CatalogCategory.SCIENCE, "de"),
        CatalogFeed("wissenschaft.de", "https://www.wissenschaft.de/feed/", CatalogCategory.SCIENCE, "de"),
        CatalogFeed("Quarks", "https://www.quarks.de/feed/", CatalogCategory.SCIENCE, "de"),
        CatalogFeed("Nature News", "https://www.nature.com/nature.rss", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("Science Daily", "https://www.sciencedaily.com/rss/all.xml", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("New Scientist", "https://www.newscientist.com/feed/home/", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("Phys.org", "https://phys.org/rss-feed/", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("Quanta Magazine", "https://www.quantamagazine.org/feed/", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", CatalogCategory.SCIENCE, "en"),

        // Wirtschaft
        CatalogFeed("Handelsblatt", "https://www.handelsblatt.com/contentexport/feed/schlagzeilen", CatalogCategory.ECONOMY, "de"),
        CatalogFeed("manager magazin", "https://www.manager-magazin.de/news/index.rss", CatalogCategory.ECONOMY, "de"),
        CatalogFeed("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", CatalogCategory.ECONOMY, "en"),

        // Kultur
        CatalogFeed("Deutschlandfunk Kultur", "https://www.deutschlandfunkkultur.de/kulturnachrichten-100.rss", CatalogCategory.CULTURE, "de"),
        CatalogFeed("ARTE Journal", "https://www.arte.tv/sites/corporate/feed/", CatalogCategory.CULTURE, "de"),
        CatalogFeed("The Guardian Culture", "https://www.theguardian.com/culture/rss", CatalogCategory.CULTURE, "en"),
    )

    /** Katalog gruppiert nach Kategorie, in Enum-Reihenfolge. */
    fun byCategory(): Map<CatalogCategory, List<CatalogFeed>> =
        feeds.groupBy { it.category }
}
