package de.hippokratius.myfeed.nextcloud

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import de.hippokratius.myfeed.backend.BackendException
import de.hippokratius.myfeed.core.nextcloud.NewsJson
import de.hippokratius.myfeed.core.nextcloud.NewsRoutes
import de.hippokratius.myfeed.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Verbindungszustand zum Nextcloud-Konto (für die Einstellungs-UI). */
sealed interface SsoState {
    data object Disconnected : SsoState
    data object Connecting : SsoState
    data class Connected(val accountName: String) : SsoState

    /** Konto verbunden, aber die News-App fehlt auf dem Server. */
    data class NewsAppMissing(val accountName: String) : SsoState

    /** Zugriff entzogen / Token ungültig – erneute Anmeldung nötig. */
    data object AuthLost : SsoState
}

/**
 * Kapselt die SSO-Bibliothek: Kontoauswahl über die Nextcloud-Files-App,
 * [NextcloudAPI]-Instanz (Requests laufen per IPC durch die Files-App –
 * Zugangsdaten berühren MyFeed nie) und das Fehler-Mapping auf
 * [BackendException].
 */
class SsoSessionManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private var api: NextcloudAPI? = null

    private val _state = MutableStateFlow<SsoState>(SsoState.Disconnected)
    val state: StateFlow<SsoState> = _state

    init {
        // Bestehende Kontowahl wiederherstellen (z. B. nach Prozess-Neustart).
        scope.launch {
            runCatching {
                SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            }.onSuccess { account ->
                _state.value = SsoState.Connected(account.name)
            }.onFailure { error ->
                _state.value = when (error) {
                    is NoCurrentAccountSelectedException -> SsoState.Disconnected
                    is NextcloudFilesAppAccountNotFoundException -> {
                        // Konto war mal verbunden, existiert aber nicht mehr.
                        if (settingsRepository.current().ncAccountName != null) {
                            SsoState.AuthLost
                        } else {
                            SsoState.Disconnected
                        }
                    }
                    else -> SsoState.Disconnected
                }
            }
        }
    }

    /**
     * Startet die Kontoauswahl in der Files-App. Wirft
     * [NextcloudFilesAppNotInstalledException], wenn die Files-App fehlt –
     * die UI zeigt dann den Play-Store-Hinweis.
     */
    fun pickAccount(activity: Activity) {
        AccountImporter.pickNewAccount(activity)
    }

    /**
     * Callback aus MainActivity.onActivityResult: Der Nutzer hat in der
     * Files-App ein Konto gewählt und den Zugriff bestätigt.
     */
    fun onAccountGranted(account: SingleSignOnAccount) {
        _state.value = SsoState.Connecting
        scope.launch {
            runCatching {
                // @WorkerThread laut Bibliothek – wir sind auf Dispatchers.IO.
                SingleAccountHelper.commitCurrentAccount(context, account.name)
                settingsRepository.setNcAccountName(account.name)
                api?.close()
                api = NextcloudAPI(context, account, gson)
                checkNewsAppInstalled()
            }.onSuccess { newsInstalled ->
                _state.value = if (newsInstalled) {
                    SsoState.Connected(account.name)
                } else {
                    SsoState.NewsAppMissing(account.name)
                }
            }.onFailure { error ->
                Log.w(TAG, "Nextcloud-Verbindung fehlgeschlagen", error)
                _state.value = SsoState.AuthLost
            }
        }
    }

    /** Trennt die App vom Konto (die Freigabe in der Files-App bleibt bestehen). */
    suspend fun disconnect() {
        api?.close()
        api = null
        settingsRepository.setNcAccountName(null)
        settingsRepository.setNcLastModified(0)
        settingsRepository.setNcLastSyncError(null)
        _state.value = SsoState.Disconnected
    }

    /** Liefert die verbundene API oder wirft [BackendException.NotConnected]. */
    suspend fun requireApi(): NextcloudAPI = withContext(Dispatchers.IO) {
        api ?: run {
            val account = try {
                SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            } catch (e: NoCurrentAccountSelectedException) {
                throw BackendException.NotConnected()
            } catch (e: NextcloudFilesAppAccountNotFoundException) {
                _state.value = SsoState.AuthLost
                throw BackendException.AuthLost(e)
            }
            NextcloudAPI(context, account, gson).also { api = it }
        }
    }

    /**
     * Übersetzt Ausnahmen der SSO-Bibliothek in [BackendException] und hält
     * den UI-Zustand aktuell. Von [SsoNewsApi] für jeden Request genutzt.
     */
    fun mapError(error: Exception): BackendException = when (error) {
        is BackendException -> error
        is TokenMismatchException,
        is NextcloudFilesAppAccountNotFoundException,
        is NoCurrentAccountSelectedException,
        -> {
            _state.value = SsoState.AuthLost
            BackendException.AuthLost(error)
        }
        is NextcloudHttpRequestFailedException -> when (error.statusCode) {
            401, 403 -> {
                _state.value = SsoState.AuthLost
                BackendException.AuthLost(error)
            }
            else -> BackendException.ServerUnreachable(error)
        }
        else -> BackendException.ServerUnreachable(error)
    }

    /** GET /version – 404/405 heißt: News-App auf dem Server nicht installiert. */
    private suspend fun checkNewsAppInstalled(): Boolean = withContext(Dispatchers.IO) {
        val route = NewsRoutes.version()
        val request = com.nextcloud.android.sso.aidl.NextcloudRequest.Builder()
            .setMethod(route.method)
            .setUrl(route.path)
            .build()
        try {
            requireApi().performNetworkRequestV2(request).body.use { stream ->
                NewsJson.parseVersion(stream)
            }
            true
        } catch (e: NextcloudHttpRequestFailedException) {
            if (e.statusCode == 404 || e.statusCode == 405) false else throw e
        }
    }

    companion object {
        private const val TAG = "SsoSessionManager"

        /** Paketname der Nextcloud-Files-App (Play-Store-Link im Fehlerdialog). */
        const val FILES_APP_PACKAGE = "com.nextcloud.client"
    }
}
