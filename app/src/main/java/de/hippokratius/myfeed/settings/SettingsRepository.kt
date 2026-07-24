package de.hippokratius.myfeed.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.hippokratius.myfeed.backend.BackendMode
import de.hippokratius.myfeed.data.Origin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val refreshIntervalMinutes: Int = 30,
    val maxAgeDays: Int = 7,
    /**
     * Aufbewahrung archivierter (geöffneter) Artikel in Tagen – immer
     * mindestens so lang wie die normale Aufbewahrungsdauer.
     */
    val archiveMaxAgeDays: Int = 30,
    /**
     * Aufbewahrung von Artikeln mit Lesezeichen in Tagen – unabhängig vom
     * Archiv einstellbar, ebenfalls nie kürzer als die normale Aufbewahrung.
     */
    val bookmarkMaxAgeDays: Int = 90,
    val showImages: Boolean = true,
    val groupingEnabled: Boolean = true,
    /** Gelesene Artikel im Reader ausblenden statt nur ausgrauen. */
    val hideRead: Boolean = false,
    /** Zeitpunkt des letzten erfolgreichen Syncs (Epoch-Millis, 0 = nie). */
    val lastSyncMillis: Long = 0,
    /** Artikel, deren Titel eines dieser Wörter enthält, werden ausgeblendet. */
    val filterWords: Set<String> = emptySet(),
    /** Aktives Backend: lokale RSS-Feeds oder Nextcloud News (exklusiv). */
    val backendMode: BackendMode = BackendMode.LOCAL_RSS,
    /** Sync-Cursor der News-API in Epoch-Sekunden, 0 = Initial-Sync ausstehend. */
    val ncLastModified: Long = 0,
    /** Anzeigename des verbundenen Kontos (Wahrheit liegt bei der SSO-Bibliothek). */
    val ncAccountName: String? = null,
    /** Letzter Nextcloud-Sync-Fehler für die Anzeige in den Einstellungen. */
    val ncLastSyncError: String? = null,
    /** §4.3: Aufbewahrungsregeln auch auf dem Server anwenden (Gelesen-Markieren). */
    val ncManageLifecycle: Boolean = false,
    /** §4.3: zusätzlich abgelaufene Lesezeichen am Server entsternen. */
    val ncManageStars: Boolean = false,
) {
    /** Herkunfts-Filter für alle lesenden Queries des aktiven Backends. */
    val activeOrigin: String
        get() = if (backendMode == BackendMode.NEXTCLOUD_NEWS) Origin.NEXTCLOUD else Origin.LOCAL

    /** Ältestes publishedAt, das im normalen Feed noch angezeigt wird. */
    fun feedCutoffMillis(now: Long): Long = now - maxAgeDays * DAY_MILLIS

    /** Cutoff fürs Archiv – nie kürzer als die normale Aufbewahrung. */
    fun archiveCutoffMillis(now: Long): Long =
        now - maxOf(archiveMaxAgeDays, maxAgeDays) * DAY_MILLIS

    /** Cutoff für Lesezeichen – nie kürzer als die normale Aufbewahrung. */
    fun bookmarkCutoffMillis(now: Long): Long =
        now - maxOf(bookmarkMaxAgeDays, maxAgeDays) * DAY_MILLIS

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val refreshInterval = intPreferencesKey("refresh_interval_minutes")
        val maxAgeDays = intPreferencesKey("max_age_days")
        val archiveMaxAgeDays = intPreferencesKey("archive_max_age_days")
        val bookmarkMaxAgeDays = intPreferencesKey("bookmark_max_age_days")
        val showImages = booleanPreferencesKey("show_images")
        val groupingEnabled = booleanPreferencesKey("grouping_enabled")
        val hideRead = booleanPreferencesKey("hide_read")
        val lastSync = longPreferencesKey("last_sync_millis")
        val filterWords = stringSetPreferencesKey("filter_words")
        val backendMode = stringPreferencesKey("backend_mode")
        val ncLastModified = longPreferencesKey("nc_last_modified")
        val ncAccountName = stringPreferencesKey("nc_account_name")
        val ncLastSyncError = stringPreferencesKey("nc_last_sync_error")
        val ncManageLifecycle = booleanPreferencesKey("nc_manage_lifecycle")
        val ncManageStars = booleanPreferencesKey("nc_manage_stars")
    }

    private companion object {
        const val MODE_LOCAL = "local"
        const val MODE_NEXTCLOUD = "nextcloud"
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            refreshIntervalMinutes = prefs[Keys.refreshInterval] ?: 30,
            maxAgeDays = prefs[Keys.maxAgeDays] ?: 7,
            archiveMaxAgeDays = prefs[Keys.archiveMaxAgeDays] ?: 30,
            bookmarkMaxAgeDays = prefs[Keys.bookmarkMaxAgeDays] ?: 90,
            showImages = prefs[Keys.showImages] ?: true,
            groupingEnabled = prefs[Keys.groupingEnabled] ?: true,
            hideRead = prefs[Keys.hideRead] ?: false,
            lastSyncMillis = prefs[Keys.lastSync] ?: 0,
            filterWords = prefs[Keys.filterWords] ?: emptySet(),
            backendMode = if (prefs[Keys.backendMode] == MODE_NEXTCLOUD) {
                BackendMode.NEXTCLOUD_NEWS
            } else {
                BackendMode.LOCAL_RSS
            },
            ncLastModified = prefs[Keys.ncLastModified] ?: 0,
            ncAccountName = prefs[Keys.ncAccountName],
            ncLastSyncError = prefs[Keys.ncLastSyncError],
            ncManageLifecycle = prefs[Keys.ncManageLifecycle] ?: false,
            ncManageStars = prefs[Keys.ncManageStars] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.refreshInterval] = minutes }
    }

    suspend fun setMaxAgeDays(days: Int) {
        context.dataStore.edit { it[Keys.maxAgeDays] = days }
    }

    suspend fun setArchiveMaxAgeDays(days: Int) {
        context.dataStore.edit { it[Keys.archiveMaxAgeDays] = days }
    }

    suspend fun setBookmarkMaxAgeDays(days: Int) {
        context.dataStore.edit { it[Keys.bookmarkMaxAgeDays] = days }
    }

    suspend fun setHideRead(hide: Boolean) {
        context.dataStore.edit { it[Keys.hideRead] = hide }
    }

    suspend fun setShowImages(show: Boolean) {
        context.dataStore.edit { it[Keys.showImages] = show }
    }

    suspend fun setGroupingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.groupingEnabled] = enabled }
    }

    suspend fun setLastSyncMillis(millis: Long) {
        context.dataStore.edit { it[Keys.lastSync] = millis }
    }

    suspend fun addFilterWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.filterWords] ?: emptySet()
            if (current.none { it.equals(trimmed, ignoreCase = true) }) {
                prefs[Keys.filterWords] = current + trimmed
            }
        }
    }

    suspend fun removeFilterWord(word: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.filterWords] ?: emptySet()
            prefs[Keys.filterWords] = current - word
        }
    }

    suspend fun setBackendMode(mode: BackendMode) {
        context.dataStore.edit {
            it[Keys.backendMode] =
                if (mode == BackendMode.NEXTCLOUD_NEWS) MODE_NEXTCLOUD else MODE_LOCAL
        }
    }

    suspend fun setNcLastModified(seconds: Long) {
        context.dataStore.edit { it[Keys.ncLastModified] = seconds }
    }

    suspend fun setNcAccountName(name: String?) {
        context.dataStore.edit { prefs ->
            if (name == null) prefs.remove(Keys.ncAccountName) else prefs[Keys.ncAccountName] = name
        }
    }

    suspend fun setNcLastSyncError(message: String?) {
        context.dataStore.edit { prefs ->
            if (message == null) {
                prefs.remove(Keys.ncLastSyncError)
            } else {
                prefs[Keys.ncLastSyncError] = message
            }
        }
    }

    suspend fun setNcManageLifecycle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ncManageLifecycle] = enabled }
    }

    suspend fun setNcManageStars(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ncManageStars] = enabled }
    }
}
