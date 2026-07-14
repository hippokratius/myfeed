package de.hippokratius.myfeed.core.catalog

/** Thema eines Katalog-Feeds; die App mappt auf lokalisierte Anzeigenamen. */
enum class CatalogCategory {
    NEWS,
    POLITICS,
    REGIONAL,
    TECH,
    GAMING,
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
        CatalogFeed("FAZ", "https://www.faz.net/rss/aktuell/", CatalogCategory.NEWS, "de"),
        CatalogFeed("ntv", "https://www.n-tv.de/rss", CatalogCategory.NEWS, "de"),
        CatalogFeed("NZZ", "https://www.nzz.ch/recent.rss", CatalogCategory.NEWS, "de"),
        CatalogFeed("Deutschlandfunk Nachrichten", "https://www.deutschlandfunk.de/nachrichten-100.rss", CatalogCategory.NEWS, "de"),
        CatalogFeed("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", CatalogCategory.NEWS, "en"),
        CatalogFeed("The Guardian", "https://www.theguardian.com/world/rss", CatalogCategory.NEWS, "en"),
        CatalogFeed("The New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", CatalogCategory.NEWS, "en"),
        CatalogFeed("NPR News", "https://feeds.npr.org/1001/rss.xml", CatalogCategory.NEWS, "en"),
        CatalogFeed("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", CatalogCategory.NEWS, "en"),

        // Politik
        CatalogFeed("Verfassungsblog", "https://verfassungsblog.de/feed/", CatalogCategory.POLITICS, "de"),
        CatalogFeed("Lage der Nation", "https://lagedernation.org/feed/", CatalogCategory.POLITICS, "de"),
        CatalogFeed("Euractiv Deutschland", "https://www.euractiv.de/feed/", CatalogCategory.POLITICS, "de"),
        CatalogFeed("Politico Europe", "https://www.politico.eu/feed/", CatalogCategory.POLITICS, "en"),
        CatalogFeed("The Guardian Politics", "https://www.theguardian.com/politics/rss", CatalogCategory.POLITICS, "en"),
        CatalogFeed("BBC Politics", "https://feeds.bbci.co.uk/news/politics/rss.xml", CatalogCategory.POLITICS, "en"),

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
        CatalogFeed("Kuketz IT-Security Blog", "https://www.kuketz-blog.de/feed/", CatalogCategory.TECH, "de"),
        CatalogFeed("The Verge", "https://www.theverge.com/rss/index.xml", CatalogCategory.TECH, "en"),
        CatalogFeed("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", CatalogCategory.TECH, "en"),
        CatalogFeed("Hacker News", "https://news.ycombinator.com/rss", CatalogCategory.TECH, "en"),
        CatalogFeed("TechCrunch", "https://techcrunch.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("WIRED", "https://www.wired.com/feed/rss", CatalogCategory.TECH, "en"),
        CatalogFeed("Engadget", "https://www.engadget.com/rss.xml", CatalogCategory.TECH, "en"),
        CatalogFeed("MIT Technology Review", "https://www.technologyreview.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("The Register", "https://www.theregister.com/headlines.atom", CatalogCategory.TECH, "en"),
        CatalogFeed("BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", CatalogCategory.TECH, "en"),
        CatalogFeed("9to5Google", "https://9to5google.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("9to5Mac", "https://9to5mac.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("Android Police", "https://www.androidpolice.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("XDA Developers", "https://www.xda-developers.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("Slashdot", "https://rss.slashdot.org/Slashdot/slashdotMain", CatalogCategory.TECH, "en"),
        CatalogFeed("Krebs on Security", "https://krebsonsecurity.com/feed/", CatalogCategory.TECH, "en"),
        CatalogFeed("Schneier on Security", "https://www.schneier.com/feed/atom/", CatalogCategory.TECH, "en"),

        // Gaming
        CatalogFeed("GamesWirtschaft", "https://www.gameswirtschaft.de/feed/", CatalogCategory.GAMING, "de"),
        CatalogFeed("Polygon", "https://www.polygon.com/rss/index.xml", CatalogCategory.GAMING, "en"),
        CatalogFeed("Kotaku", "https://kotaku.com/rss", CatalogCategory.GAMING, "en"),
        CatalogFeed("PC Gamer", "https://www.pcgamer.com/rss/", CatalogCategory.GAMING, "en"),
        CatalogFeed("Rock Paper Shotgun", "https://www.rockpapershotgun.com/feed", CatalogCategory.GAMING, "en"),

        // Sport
        CatalogFeed("Sportschau", "https://www.sportschau.de/index~rss2.xml", CatalogCategory.SPORT, "de"),
        CatalogFeed("kicker", "https://newsfeed.kicker.de/news/aktuell", CatalogCategory.SPORT, "de"),
        CatalogFeed("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", CatalogCategory.SPORT, "en"),
        CatalogFeed("ESPN", "https://www.espn.com/espn/rss/news", CatalogCategory.SPORT, "en"),
        CatalogFeed("Sky Sports News", "https://www.skysports.com/rss/12040", CatalogCategory.SPORT, "en"),
        CatalogFeed("The Guardian Football", "https://www.theguardian.com/football/rss", CatalogCategory.SPORT, "en"),

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
        CatalogFeed("NASA Breaking News", "https://www.nasa.gov/rss/dyn/breaking_news.rss", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("Live Science", "https://www.livescience.com/feeds/all", CatalogCategory.SCIENCE, "en"),
        CatalogFeed("The Guardian Environment", "https://www.theguardian.com/environment/rss", CatalogCategory.SCIENCE, "en"),

        // Wirtschaft
        CatalogFeed("Handelsblatt", "https://www.handelsblatt.com/contentexport/feed/schlagzeilen", CatalogCategory.ECONOMY, "de"),
        CatalogFeed("WirtschaftsWoche", "https://www.wiwo.de/contentexport/feed/schlagzeilen", CatalogCategory.ECONOMY, "de"),
        CatalogFeed("manager magazin", "https://www.manager-magazin.de/news/index.rss", CatalogCategory.ECONOMY, "de"),
        CatalogFeed("BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", CatalogCategory.ECONOMY, "en"),
        CatalogFeed("The Economist Business", "https://www.economist.com/business/rss.xml", CatalogCategory.ECONOMY, "en"),
        CatalogFeed("The Guardian Business", "https://www.theguardian.com/business/rss", CatalogCategory.ECONOMY, "en"),

        // Kultur
        CatalogFeed("Deutschlandfunk Kultur", "https://www.deutschlandfunkkultur.de/kulturnachrichten-100.rss", CatalogCategory.CULTURE, "de"),
        CatalogFeed("ARTE Journal", "https://www.arte.tv/sites/corporate/feed/", CatalogCategory.CULTURE, "de"),
        CatalogFeed("The Guardian Culture", "https://www.theguardian.com/culture/rss", CatalogCategory.CULTURE, "en"),
        CatalogFeed("BBC Entertainment & Arts", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", CatalogCategory.CULTURE, "en"),
        CatalogFeed("Pitchfork", "https://pitchfork.com/rss/news/", CatalogCategory.CULTURE, "en"),
        CatalogFeed("Rolling Stone", "https://www.rollingstone.com/feed/", CatalogCategory.CULTURE, "en"),
        CatalogFeed("Open Culture", "https://www.openculture.com/feed", CatalogCategory.CULTURE, "en"),
    )

    /** Katalog gruppiert nach Kategorie, in Enum-Reihenfolge. */
    fun byCategory(): Map<CatalogCategory, List<CatalogFeed>> =
        feeds.groupBy { it.category }
}
