package de.hippokratius.myfeed.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppNotInstalledException
import de.hippokratius.myfeed.AppGraph
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.backend.BackendMode
import de.hippokratius.myfeed.fetch.FeedFetchWorker
import de.hippokratius.myfeed.nextcloud.SsoSessionManager
import de.hippokratius.myfeed.nextcloud.SsoState
import de.hippokratius.myfeed.settings.AppSettings
import kotlinx.coroutines.launch

/**
 * Einstellungs-Sektion "Nextcloud": Konto verbinden (SSO über die Files-App),
 * Backend-Modus umschalten, Server-Lebenszyklus (§4.3) und Konto trennen.
 */
@Composable
internal fun NextcloudSettingsSection(graph: AppGraph, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ssoState by graph.ssoSessionManager.state.collectAsState()
    val ncMode = settings.backendMode == BackendMode.NEXTCLOUD_NEWS

    var showSwitchDialog by remember { mutableStateOf<BackendMode?>(null) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showLifecycleDialog by remember { mutableStateOf(false) }
    var showStarsDialog by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.settings_nextcloud))

    when (val state = ssoState) {
        is SsoState.Disconnected, is SsoState.Connecting -> {
            Text(
                text = stringResource(R.string.nextcloud_connect_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(
                enabled = state !is SsoState.Connecting,
                onClick = { pickNextcloudAccount(context.findActivity(), graph.ssoSessionManager) },
            ) {
                Text(
                    stringResource(
                        if (state is SsoState.Connecting) {
                            R.string.nextcloud_connect_checking
                        } else {
                            R.string.nextcloud_connect
                        },
                    ),
                )
            }
        }

        is SsoState.AuthLost -> {
            Text(
                text = stringResource(R.string.nextcloud_auth_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(onClick = { pickNextcloudAccount(context.findActivity(), graph.ssoSessionManager) }) {
                Text(stringResource(R.string.nextcloud_connect))
            }
        }

        is SsoState.NewsAppMissing -> {
            Text(
                text = stringResource(R.string.nextcloud_connected_as, state.accountName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.nextcloud_news_app_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = { showDisconnectDialog = true }) {
                Text(stringResource(R.string.nextcloud_disconnect))
            }
        }

        is SsoState.Connected -> {
            Text(
                text = stringResource(R.string.nextcloud_connected_as, state.accountName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Modus-Umschalter: lokale Feeds oder Nextcloud News (exklusiv).
            SectionTitle(stringResource(R.string.nextcloud_mode_title))
            RadioRow(
                label = stringResource(R.string.nextcloud_mode_local),
                selected = !ncMode,
                onClick = { if (ncMode) showSwitchDialog = BackendMode.LOCAL_RSS },
            )
            RadioRow(
                label = stringResource(R.string.nextcloud_mode_nextcloud),
                selected = ncMode,
                onClick = { if (!ncMode) showSwitchDialog = BackendMode.NEXTCLOUD_NEWS },
            )

            if (ncMode) {
                // Server-Lebenszyklus (§4.3): Opt-in, Entsternen als Unteroption.
                SwitchRow(
                    label = stringResource(R.string.nextcloud_lifecycle),
                    checked = settings.ncManageLifecycle,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showLifecycleDialog = true
                        } else {
                            scope.launch { graph.settingsRepository.setNcManageLifecycle(false) }
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.nextcloud_lifecycle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (settings.ncManageLifecycle) {
                    SwitchRow(
                        label = stringResource(R.string.nextcloud_lifecycle_stars),
                        checked = settings.ncManageStars,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showStarsDialog = true
                            } else {
                                scope.launch { graph.settingsRepository.setNcManageStars(false) }
                            }
                        },
                    )
                }

                settings.ncLastSyncError?.let { error ->
                    Text(
                        text = stringResource(R.string.nextcloud_sync_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            TextButton(onClick = { showDisconnectDialog = true }) {
                Text(stringResource(R.string.nextcloud_disconnect))
            }
        }
    }

    // Bestätigung Modus-Wechsel (Datenbestände bleiben erhalten, E3).
    showSwitchDialog?.let { targetMode ->
        AlertDialog(
            onDismissRequest = { showSwitchDialog = null },
            title = { Text(stringResource(R.string.nextcloud_switch_confirm_title)) },
            text = { Text(stringResource(R.string.nextcloud_switch_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSwitchDialog = null
                        scope.launch {
                            graph.backendRegistry.switchTo(targetMode)
                            FeedFetchWorker.syncNow(context)
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchDialog = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDisconnectDialog) {
        var deleteData by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.nextcloud_disconnect_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.nextcloud_disconnect_confirm_message))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteData, onCheckedChange = { deleteData = it })
                        Text(
                            text = stringResource(R.string.nextcloud_disconnect_delete_data),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        scope.launch {
                            graph.backendRegistry.switchTo(BackendMode.LOCAL_RSS)
                            graph.ssoSessionManager.disconnect()
                            if (deleteData) {
                                graph.nextcloudNewsBackend.wipeLocalMirror()
                            }
                            graph.syncPostProcessor.regroupAndRefreshWidget()
                        }
                    },
                ) {
                    Text(stringResource(R.string.nextcloud_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showLifecycleDialog) {
        AlertDialog(
            onDismissRequest = { showLifecycleDialog = false },
            title = { Text(stringResource(R.string.nextcloud_lifecycle_confirm_title)) },
            text = { Text(stringResource(R.string.nextcloud_lifecycle_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLifecycleDialog = false
                        scope.launch { graph.settingsRepository.setNcManageLifecycle(true) }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLifecycleDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showStarsDialog) {
        AlertDialog(
            onDismissRequest = { showStarsDialog = false },
            title = { Text(stringResource(R.string.nextcloud_lifecycle_confirm_title)) },
            text = { Text(stringResource(R.string.nextcloud_lifecycle_stars_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStarsDialog = false
                        scope.launch { graph.settingsRepository.setNcManageStars(true) }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStarsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Compose-Context kann ein Wrapper sein – die Activity dahinter suchen. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Kontoauswahl in der Files-App starten; ohne Files-App: Play-Store-Hinweis. */
private fun pickNextcloudAccount(activity: Activity?, sessionManager: SsoSessionManager) {
    if (activity == null) return
    try {
        sessionManager.pickAccount(activity)
    } catch (e: NextcloudFilesAppNotInstalledException) {
        Toast.makeText(
            activity,
            activity.getString(R.string.nextcloud_files_app_missing),
            Toast.LENGTH_LONG,
        ).show()
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${SsoSessionManager.FILES_APP_PACKAGE}"),
        )
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${SsoSessionManager.FILES_APP_PACKAGE}"),
        )
        runCatching { activity.startActivity(market) }
            .recoverCatching { activity.startActivity(web) }
    } catch (e: Exception) {
        Toast.makeText(activity, e.localizedMessage, Toast.LENGTH_LONG).show()
    }
}
