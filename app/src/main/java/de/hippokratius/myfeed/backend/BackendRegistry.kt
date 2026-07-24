package de.hippokratius.myfeed.backend

import de.hippokratius.myfeed.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Liefert das aktive Backend anhand des Modus in den Einstellungen. */
class BackendRegistry(
    private val settingsRepository: SettingsRepository,
    private val local: LocalRssBackend,
    private val nextcloud: FeedBackend,
) {

    suspend fun current(): FeedBackend =
        if (settingsRepository.current().backendMode == BackendMode.NEXTCLOUD_NEWS) {
            nextcloud
        } else {
            local
        }

    val modeFlow: Flow<BackendMode> =
        settingsRepository.settings.map { it.backendMode }.distinctUntilChanged()

    /** Modus umschalten; der nächste Sync bedient das neue Backend. */
    suspend fun switchTo(mode: BackendMode) {
        settingsRepository.setBackendMode(mode)
    }
}
