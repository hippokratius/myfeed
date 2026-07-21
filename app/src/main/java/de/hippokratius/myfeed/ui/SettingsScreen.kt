package de.hippokratius.myfeed.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val REFRESH_INTERVALS = listOf(15, 30, 60, 180)
private val MAX_AGES = listOf(3, 7, 14)

/** Aufbewahrung für Archiv bzw. Lesezeichen – nie kürzer als die normale Aufbewahrung wirksam. */
private val SAVED_MAX_AGES = listOf(7, 14, 30, 60)

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
            SteppedSliderRow(
                values = REFRESH_INTERVALS,
                selectedValue = settings.refreshIntervalMinutes,
                labelRes = R.string.settings_minutes,
                onValueSelected = { minutes ->
                    scope.launch { graph.settingsRepository.setRefreshIntervalMinutes(minutes) }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_max_age))
            SteppedSliderRow(
                values = MAX_AGES,
                selectedValue = settings.maxAgeDays,
                labelRes = R.string.settings_days,
                onValueSelected = { days ->
                    scope.launch { graph.settingsRepository.setMaxAgeDays(days) }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_archive_max_age))
            Text(
                text = stringResource(R.string.settings_archive_max_age_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SteppedSliderRow(
                values = SAVED_MAX_AGES,
                selectedValue = settings.archiveMaxAgeDays,
                labelRes = R.string.settings_days,
                onValueSelected = { days ->
                    scope.launch { graph.settingsRepository.setArchiveMaxAgeDays(days) }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionTitle(stringResource(R.string.settings_bookmark_max_age))
            Text(
                text = stringResource(R.string.settings_bookmark_max_age_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SteppedSliderRow(
                values = SAVED_MAX_AGES,
                selectedValue = settings.bookmarkMaxAgeDays,
                labelRes = R.string.settings_days,
                onValueSelected = { days ->
                    scope.launch { graph.settingsRepository.setBookmarkMaxAgeDays(days) }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SwitchRow(
                label = stringResource(R.string.settings_show_images),
                checked = settings.showImages,
                onCheckedChange = { checked ->
                    scope.launch {
                        graph.settingsRepository.setShowImages(checked)
                        graph.feedSyncer.regroupAndRefreshWidget()
                    }
                },
            )
            SwitchRow(
                label = stringResource(R.string.settings_grouping),
                checked = settings.groupingEnabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        graph.settingsRepository.setGroupingEnabled(checked)
                        graph.feedSyncer.regroupAndRefreshWidget()
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
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Slider, der nur auf den Positionen aus [values] einrastet; Marker werden beschriftet. */
@Composable
private fun SteppedSliderRow(
    values: List<Int>,
    selectedValue: Int,
    @StringRes labelRes: Int,
    onValueSelected: (Int) -> Unit,
) {
    val selectedIndex = values.indexOf(selectedValue).takeIf { it >= 0 }
        ?: values.indices.minBy { abs(values[it] - selectedValue) }
    var sliderIndex by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val currentValue = values[sliderIndex.roundToInt().coerceIn(values.indices)]

    Text(
        text = stringResource(labelRes, currentValue),
        style = MaterialTheme.typography.bodyLarge,
    )
    Slider(
        value = sliderIndex,
        onValueChange = { sliderIndex = it },
        valueRange = 0f..(values.size - 1).toFloat(),
        steps = values.size - 2,
        onValueChangeFinished = {
            val value = values[sliderIndex.roundToInt().coerceIn(values.indices)]
            if (value != selectedValue) onValueSelected(value)
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        values.forEach { value ->
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
