package de.hippokratius.myfeed.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.hippokratius.myfeed.AppGraph
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.settings.AppSettings
import de.hippokratius.myfeed.widget.RssWidget
import kotlinx.coroutines.launch

private val REFRESH_INTERVALS = listOf(15, 30, 60, 180)
private val MAX_AGES = listOf(3, 7, 14)

/** Verlängerte Aufbewahrung für Archiv bzw. Lesezeichen – länger als der Feed. */
private val SAVED_MAX_AGES = listOf(14, 30, 90, 365)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.settingsRepository.settings.collectAsState(initial = AppSettings())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_refresh_interval))
            REFRESH_INTERVALS.forEach { minutes ->
                RadioRow(
                    label = stringResource(R.string.settings_minutes, minutes),
                    selected = settings.refreshIntervalMinutes == minutes,
                    onClick = {
                        scope.launch { graph.settingsRepository.setRefreshIntervalMinutes(minutes) }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_max_age))
            MAX_AGES.forEach { days ->
                RadioRow(
                    label = stringResource(R.string.settings_days, days),
                    selected = settings.maxAgeDays == days,
                    onClick = {
                        scope.launch { graph.settingsRepository.setMaxAgeDays(days) }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_archive_max_age))
            Text(
                text = stringResource(R.string.settings_archive_max_age_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SAVED_MAX_AGES.forEach { days ->
                RadioRow(
                    label = stringResource(R.string.settings_days, days),
                    selected = settings.archiveMaxAgeDays == days,
                    onClick = {
                        scope.launch { graph.settingsRepository.setArchiveMaxAgeDays(days) }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_bookmark_max_age))
            Text(
                text = stringResource(R.string.settings_bookmark_max_age_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SAVED_MAX_AGES.forEach { days ->
                RadioRow(
                    label = stringResource(R.string.settings_days, days),
                    selected = settings.bookmarkMaxAgeDays == days,
                    onClick = {
                        scope.launch { graph.settingsRepository.setBookmarkMaxAgeDays(days) }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SwitchRow(
                label = stringResource(R.string.settings_show_images),
                checked = settings.showImages,
                onCheckedChange = { checked ->
                    scope.launch {
                        graph.settingsRepository.setShowImages(checked)
                        graph.syncPostProcessor.regroupAndRefreshWidget()
                    }
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_grouping),
                checked = settings.groupingEnabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        graph.settingsRepository.setGroupingEnabled(checked)
                        graph.syncPostProcessor.regroupAndRefreshWidget()
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_word_filter))
            Text(
                text = stringResource(R.string.settings_word_filter_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            var newWord by remember { mutableStateOf("") }
            val addWord: () -> Unit = {
                val word = newWord.trim()
                if (word.isNotEmpty()) {
                    newWord = ""
                    scope.launch {
                        graph.settingsRepository.addFilterWord(word)
                        RssWidget.updateAll(context)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text(stringResource(R.string.settings_word_filter_add_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addWord() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = addWord, enabled = newWord.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.settings_word_filter_add_label),
                    )
                }
            }

            settings.filterWords.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { word ->
                FilterWordRow(
                    word = word,
                    onDelete = {
                        scope.launch {
                            graph.settingsRepository.removeFilterWord(word)
                            RssWidget.updateAll(context)
                        }
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            NextcloudSettingsSection(graph = graph, settings = settings)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FilterWordRow(word: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.settings_word_filter_remove, word),
            )
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
