package de.hippokratius.myfeed.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.hippokratius.myfeed.MyFeedApp
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.ui.AppTheme
import kotlinx.coroutines.launch

/**
 * Konfigurations-Activity des Widgets (APPWIDGET_CONFIGURE): wählt pro
 * Widget-Instanz "Alle Kategorien" oder eine Kategorie. Die Auswahl landet im
 * Glance-Per-Instanz-State ([RssWidget.KEY_CATEGORY]) und wird beim Entfernen
 * des Widgets automatisch mit aufgeräumt.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultIntent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val graph = (application as MyFeedApp).graph
        setContent {
            AppTheme {
                ConfigScreen(
                    loadInitial = {
                        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
                        val categories = graph.feedDao.getCategories()
                        val current = getAppWidgetState(
                            this,
                            PreferencesGlanceStateDefinition,
                            glanceId,
                        )[RssWidget.KEY_CATEGORY]
                        categories to current
                    },
                    onSave = { category ->
                        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
                        updateAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                            prefs.toMutablePreferences().apply {
                                if (category == null) {
                                    remove(RssWidget.KEY_CATEGORY)
                                } else {
                                    this[RssWidget.KEY_CATEGORY] = category
                                }
                            }
                        }
                        RssWidget().update(this, glanceId)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    loadInitial: suspend () -> Pair<List<String>, String?>,
    onSave: suspend (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initial by produceState<Pair<List<String>, String?>?>(initialValue = null) {
        value = loadInitial()
    }
    var selected by remember(initial) { mutableStateOf(initial?.second) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { padding ->
        val loaded = initial ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.widget_config_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ConfigOptionRow(
                label = stringResource(R.string.widget_config_all_categories),
                selected = selected == null,
                onClick = { selected = null },
            )
            for (category in loaded.first) {
                ConfigOptionRow(
                    label = category,
                    selected = selected == category,
                    onClick = { selected = category },
                )
            }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch { onSave(selected) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ConfigOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
