package de.hippokratius.kvaesitsorss.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val refreshIntervalMinutes: Int = 30,
    val maxAgeDays: Int = 7,
    val showImages: Boolean = true,
    val groupingEnabled: Boolean = true,
    /** Zeitpunkt des letzten erfolgreichen Syncs (Epoch-Millis, 0 = nie). */
    val lastSyncMillis: Long = 0,
    /** Artikel, deren Titel eines dieser Wörter enthält, werden ausgeblendet. */
    val filterWords: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val refreshInterval = intPreferencesKey("refresh_interval_minutes")
        val maxAgeDays = intPreferencesKey("max_age_days")
        val showImages = booleanPreferencesKey("show_images")
        val groupingEnabled = booleanPreferencesKey("grouping_enabled")
        val lastSync = longPreferencesKey("last_sync_millis")
        val filterWords = stringSetPreferencesKey("filter_words")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            refreshIntervalMinutes = prefs[Keys.refreshInterval] ?: 30,
            maxAgeDays = prefs[Keys.maxAgeDays] ?: 7,
            showImages = prefs[Keys.showImages] ?: true,
            groupingEnabled = prefs[Keys.groupingEnabled] ?: true,
            lastSyncMillis = prefs[Keys.lastSync] ?: 0,
            filterWords = prefs[Keys.filterWords] ?: emptySet(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.refreshInterval] = minutes }
    }

    suspend fun setMaxAgeDays(days: Int) {
        context.dataStore.edit { it[Keys.maxAgeDays] = days }
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
}
