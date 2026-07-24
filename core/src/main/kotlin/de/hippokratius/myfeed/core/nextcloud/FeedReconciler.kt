package de.hippokratius.myfeed.core.nextcloud

/** Lokaler Spiegel eines Server-Feeds (Auszug aus der feeds-Tabelle). */
data class LocalNcFeed(
    val remoteId: Long,
    val url: String,
    val title: String,
    val category: String?,
    val iconUrl: String?,
)

/** Ein vom Server neu zu übernehmender Feed. */
data class NcFeedInsert(
    val remoteId: Long,
    val url: String,
    val title: String,
    val category: String?,
    val iconUrl: String?,
)

/** Änderungen an einem bereits gespiegelten Feed. */
data class NcFeedUpdate(
    val remoteId: Long,
    val title: String,
    val category: String?,
    val iconUrl: String?,
)

data class NcFeedChangeSet(
    val inserts: List<NcFeedInsert>,
    val updates: List<NcFeedUpdate>,
    /** Feeds, die der Server nicht mehr kennt – lokal samt Artikeln entfernen. */
    val deleteRemoteIds: List<Long>,
)

/**
 * Diff zwischen Server-Feeds/-Ordnern und dem lokalen Nextcloud-Spiegel.
 * Der Server ist die Quelle der Wahrheit; Ordnernamen werden zu Kategorien.
 */
object FeedReconciler {

    fun reconcile(
        local: List<LocalNcFeed>,
        serverFeeds: List<NewsFeed>,
        serverFolders: List<NewsFolder>,
    ): NcFeedChangeSet {
        val folderNames = serverFolders.associate { it.id to it.name.ifBlank { null } }
        val localByRemoteId = local.associateBy { it.remoteId }
        val serverIds = HashSet<Long>(serverFeeds.size)

        val inserts = mutableListOf<NcFeedInsert>()
        val updates = mutableListOf<NcFeedUpdate>()

        for (feed in serverFeeds) {
            serverIds.add(feed.id)
            val category = feed.folderId
                ?.takeIf { it != 0L }
                ?.let { folderNames[it] }
            val title = feed.title?.ifBlank { null }
                ?: feed.url
                ?: feed.id.toString()

            val existing = localByRemoteId[feed.id]
            if (existing == null) {
                inserts += NcFeedInsert(
                    remoteId = feed.id,
                    // url fehlt in Randfällen (Server-Fehlerzustand): Feed trotzdem
                    // spiegeln, als stabiler Schlüssel dient dann die remoteId.
                    url = feed.url ?: "nc-feed-${feed.id}",
                    title = title,
                    category = category,
                    iconUrl = feed.faviconLink?.ifBlank { null },
                )
            } else {
                val newIcon = feed.faviconLink?.ifBlank { null } ?: existing.iconUrl
                if (existing.title != title || existing.category != category || existing.iconUrl != newIcon) {
                    updates += NcFeedUpdate(
                        remoteId = feed.id,
                        title = title,
                        category = category,
                        iconUrl = newIcon,
                    )
                }
            }
        }

        val deletes = local.map { it.remoteId }.filterNot { it in serverIds }
        return NcFeedChangeSet(inserts, updates, deletes)
    }
}
