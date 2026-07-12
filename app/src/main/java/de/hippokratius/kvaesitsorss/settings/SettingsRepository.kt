package de.hippokratius.kvaesitsorss.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val refreshIntervalMinutes: Int = 30,
    val maxAgeDays: Int = 7,
    val showImages: Boolean = true,
    val groupingEnabled: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val refreshInterval = intPreferencesKey("refresh_interval_minutes")
        val maxAgeDays = intPreferencesKey("max_age_days")
        val showImages = booleanPreferencesKey("show_images")
        val groupingEnabled = booleanPreferencesKey("grouping_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            refreshIntervalMinutes = prefs[Keys.refreshInterval] ?: 30,
            maxAgeDays = prefs[Keys.maxAgeDays] ?: 7,
            showImages = prefs[Keys.showImages] ?: true,
            groupingEnabled = prefs[Keys.groupingEnabled] ?: true,
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
}
