package de.hippokratius.kvaesitsorss.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hippokratius.kvaesitsorss.AppGraph
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.core.opml.OpmlParser
import de.hippokratius.kvaesitsorss.data.FeedEntity
import de.hippokratius.kvaesitsorss.fetch.FeedFetchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filter-Sentinel für "Ohne Kategorie" (echte Kategorien sind nie leer). */
private const val FILTER_UNCATEGORIZED = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(
    graph: AppGraph,
    onOpenSettings: () -> Unit,
    onOpenDiscover: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feeds by graph.feedDao.observeAll().collectAsState(initial = emptyList())
    val categories by graph.feedDao.observeCategories().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editCategoryFeed by remember { mutableStateOf<FeedEntity?>(null) }

    // null = alle, "" = ohne Kategorie, sonst Kategoriename.
    var selectedFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val hasUncategorized = feeds.any { it.category.isNullOrBlank() }
    LaunchedEffect(categories, hasUncategorized, feeds.isEmpty()) {
        val current = selectedFilter
        val gone = when {
            current == null -> false
            current == FILTER_UNCATEGORIZED -> !hasUncategorized
            else -> current !in categories
        }
        if (gone) selectedFilter = null
    }
    val visibleFeeds = when (selectedFilter) {
        null -> feeds
        FILTER_UNCATEGORIZED -> feeds.filter { it.category.isNullOrBlank() }
        else -> feeds.filter { it.category == selectedFilter }
    }

    val opmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        OpmlParser.parse(stream)
                    }.orEmpty()
                }.getOrDefault(emptyList())
            }
            var added = 0
            for (feed in imported) {
                val id = graph.feedDao.insert(
                    FeedEntity(url = feed.xmlUrl, title = feed.title.orEmpty(), category = feed.category),
                )
                if (id != -1L) added++
            }
            Toast.makeText(
                context,
                context.getString(R.string.opml_imported, added),
                Toast.LENGTH_SHORT,
            ).show()
            if (added > 0) FeedFetchWorker.syncNow(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { FeedFetchWorker.syncNow(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_discover)) },
                            onClick = {
                                menuOpen = false
                                onOpenDiscover()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_import_opml)) },
                            onClick = {
                                menuOpen = false
                                opmlLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_settings)) },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_feed))
            }
        },
    ) { padding ->
        if (feeds.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.feeds_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onOpenDiscover, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.action_discover))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (categories.isNotEmpty()) {
                    CategoryFilterRow(
                        categories = categories,
                        hasUncategorized = hasUncategorized,
                        selected = selectedFilter,
                        onSelect = { selectedFilter = it },
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleFeeds, key = { it.id }) { feed ->
                        FeedRow(
                            feed = feed,
                            onToggle = { enabled ->
                                scope.launch {
                                    graph.feedDao.update(feed.copy(enabled = enabled))
                                    graph.feedSyncer.regroupAndRefreshWidget()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    graph.feedDao.delete(feed)
                                    graph.feedSyncer.regroupAndRefreshWidget()
                                }
                            },
                            onEditCategory = { editCategoryFeed = feed },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddFeedDialog(
            graph = graph,
            categories = categories,
            onDismiss = { showAddDialog = false },
        )
    }

    editCategoryFeed?.let { feed ->
        CategoryDialog(
            feed = feed,
            categories = categories,
            onDismiss = { editCategoryFeed = null },
            onSave = { category ->
                editCategoryFeed = null
                scope.launch {
                    graph.feedDao.updateCategory(feed.id, category)
                    graph.feedSyncer.regroupAndRefreshWidget()
                }
            },
        )
    }
}

/** Horizontale Chip-Zeile: "Alle" + Kategorien + ggf. "Ohne Kategorie". */
@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    hasUncategorized: Boolean,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.category_all)) },
        )
        for (category in categories) {
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
        if (hasUncategorized) {
            FilterChip(
                selected = selected == FILTER_UNCATEGORIZED,
                onClick = { onSelect(FILTER_UNCATEGORIZED) },
                label = { Text(stringResource(R.string.category_none)) },
            )
        }
    }
}

@Composable
private fun FeedRow(
    feed: FeedEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditCategory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.title.ifBlank { feed.url },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = feed.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            AssistChip(
                onClick = onEditCategory,
                label = {
                    Text(feed.category?.takeIf { it.isNotBlank() } ?: stringResource(R.string.category_none))
                },
            )
        }
        Switch(checked = feed.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

/**
 * Kategorie eines Feeds ändern: vorhandene Kategorien als Radio-Liste,
 * "Ohne Kategorie" oder eine frei benannte neue Kategorie.
 */
@Composable
private fun CategoryDialog(
    feed: FeedEntity,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var selected by remember { mutableStateOf(feed.category?.takeIf { it.isNotBlank() }) }
    var newCategory by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit_category)) },
        text = {
            Column {
                CategoryOptionRow(
                    label = stringResource(R.string.category_none),
                    selected = newCategory.isBlank() && selected == null,
                    onClick = {
                        selected = null
                        newCategory = ""
                    },
                )
                for (category in categories) {
                    CategoryOptionRow(
                        label = category,
                        selected = newCategory.isBlank() && selected == category,
                        onClick = {
                            selected = category
                            newCategory = ""
                        },
                    )
                }
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    label = { Text(stringResource(R.string.feed_category_new)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newCategory.trim().ifBlank { null } ?: selected) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CategoryOptionRow(
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

@Composable
private fun AddFeedDialog(
    graph: AppGraph,
    categories: List<String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.action_add_feed)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.feed_url_label)) },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.feed_category_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (existing in categories) {
                            AssistChip(onClick = { category = existing }, label = { Text(existing) })
                        }
                    }
                }
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && url.isNotBlank(),
                onClick = {
                    val normalized = url.trim().let {
                        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
                    }
                    loading = true
                    scope.launch {
                        val parsed = runCatching { graph.feedSyncer.probe(normalized) }
                        loading = false
                        parsed.fold(
                            onSuccess = { feed ->
                                graph.feedDao.insert(
                                    FeedEntity(
                                        url = normalized,
                                        title = feed.title.orEmpty(),
                                        category = category.trim().ifBlank { null },
                                    ),
                                )
                                FeedFetchWorker.syncNow(context)
                                onDismiss()
                            },
                            onFailure = {
                                error = context.getString(R.string.feed_url_invalid)
                            },
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
