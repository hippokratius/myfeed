package de.hippokratius.kvaesitsorss.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.hippokratius.kvaesitsorss.AppGraph
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.core.catalog.FeedUrls
import de.hippokratius.kvaesitsorss.core.discovery.DiscoveredFeed
import de.hippokratius.kvaesitsorss.core.opml.OpmlParser
import de.hippokratius.kvaesitsorss.data.FeedEntity
import de.hippokratius.kvaesitsorss.fetch.FeedFetchWorker
import de.hippokratius.kvaesitsorss.fetch.FeedResolution
import java.io.IOException
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filter-Sentinel für "Ohne Kategorie" (echte Kategorien sind nie leer). */
private const val FILTER_UNCATEGORIZED = ""

/** Ein Abschnitt der Feed-Liste; category = null steht für "Ohne Kategorie". */
private data class FeedSection(val category: String?, val feeds: List<FeedEntity>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(
    graph: AppGraph,
    onBack: () -> Unit,
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
    var deleteFeed by remember { mutableStateOf<FeedEntity?>(null) }

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
    // Locale-abhängige Sortierung (COLLATE NOCASE in SQL kennt keine Umlaute).
    val collator = remember { Collator.getInstance() }
    val byTitle = remember(collator) {
        compareBy(collator) { feed: FeedEntity -> feed.title.ifBlank { feed.url } }
    }
    val sections = remember(feeds, byTitle) {
        val grouped = feeds.groupBy { it.category?.takeIf { c -> c.isNotBlank() } }
        buildList {
            grouped.keys.filterNotNull()
                .sortedWith(compareBy(collator) { it })
                .forEach { category ->
                    add(FeedSection(category, grouped.getValue(category).sortedWith(byTitle)))
                }
            grouped[null]?.let { add(FeedSection(null, it.sortedWith(byTitle))) }
        }
    }
    val visibleFeeds = remember(feeds, selectedFilter, byTitle) {
        when (selectedFilter) {
            null -> feeds
            FILTER_UNCATEGORIZED -> feeds.filter { it.category.isNullOrBlank() }
            else -> feeds.filter { it.category == selectedFilter }
        }.sortedWith(byTitle)
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
                title = { Text(stringResource(R.string.action_manage_feeds)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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
                val feedRow: @Composable (FeedEntity) -> Unit = { feed ->
                    FeedRow(
                        feed = feed,
                        onToggle = { enabled ->
                            scope.launch {
                                graph.feedDao.update(feed.copy(enabled = enabled))
                                graph.feedSyncer.regroupAndRefreshWidget()
                            }
                        },
                        onDelete = { deleteFeed = feed },
                        onEditCategory = { editCategoryFeed = feed },
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (selectedFilter == null) {
                        for (section in sections) {
                            item(key = "header-${section.category.orEmpty()}", contentType = "header") {
                                FeedSectionHeader(
                                    title = section.category ?: stringResource(R.string.category_none),
                                    count = section.feeds.size,
                                )
                            }
                            items(section.feeds, key = { it.id }, contentType = { "feed" }) { feed ->
                                feedRow(feed)
                            }
                        }
                    } else {
                        items(visibleFeeds, key = { it.id }, contentType = { "feed" }) { feed ->
                            feedRow(feed)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val addedUrls = remember(feeds) { feeds.map { FeedUrls.canonical(it.url) }.toSet() }
        AddFeedDialog(
            graph = graph,
            categories = categories,
            addedUrls = addedUrls,
            // Im Screen-Scope einfügen: Der Dialog-Scope stirbt beim Schließen sofort.
            onAdd = { feedUrl, title, feedCategory ->
                scope.launch {
                    graph.feedDao.insert(
                        FeedEntity(url = feedUrl, title = title.orEmpty(), category = feedCategory),
                    )
                    FeedFetchWorker.syncNow(context)
                }
            },
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

    deleteFeed?.let { feed ->
        AlertDialog(
            onDismissRequest = { deleteFeed = null },
            title = { Text(stringResource(R.string.feed_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.feed_delete_confirm_message, feed.title.ifBlank { feed.url }))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteFeed = null
                        scope.launch {
                            graph.feedDao.delete(feed)
                            graph.feedSyncer.regroupAndRefreshWidget()
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFeed = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Abschnitts-Überschrift mit Kategoriename und Feed-Anzahl. */
@Composable
private fun FeedSectionHeader(title: String, count: Int) {
    Text(
        text = stringResource(R.string.category_header_count, title, count),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
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

/** Kompakte Feed-Zeile; Tippen auf die Zeile öffnet den Kategorie-Dialog. */
@Composable
private fun FeedRow(
    feed: FeedEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditCategory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditCategory)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.title.ifBlank { feed.url },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = feed.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/** Schritte des "Feed hinzufügen"-Dialogs. */
private sealed interface AddFeedStep {
    data object Input : AddFeedStep
    data object Loading : AddFeedStep
    data class Suggestions(val feeds: List<DiscoveredFeed>) : AddFeedStep
}

@Composable
private fun AddFeedDialog(
    graph: AppGraph,
    categories: List<String>,
    addedUrls: Set<String>,
    onAdd: (url: String, title: String?, category: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<AddFeedStep>(AddFeedStep.Input) }
    var error by remember { mutableStateOf<String?>(null) }

    val loading = step == AddFeedStep.Loading
    val suggestions = step as? AddFeedStep.Suggestions

    fun addFeed(feedUrl: String, title: String?) {
        onAdd(feedUrl, title, category.trim().ifBlank { null })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_feed)) },
        text = {
            Column {
                if (suggestions == null) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = null
                        },
                        label = { Text(stringResource(R.string.feed_url_or_site_label)) },
                        placeholder = { Text(stringResource(R.string.feed_url_placeholder)) },
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
                } else {
                    Text(
                        text = stringResource(R.string.feed_discovery_found),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
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
                if (suggestions != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        for (candidate in suggestions.feeds) {
                            FeedSuggestionRow(
                                title = candidate.title ?: candidate.url,
                                subtitle = candidate.url,
                                added = FeedUrls.canonical(candidate.url) in addedUrls,
                                onAdd = { addFeed(candidate.url, candidate.title) },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (suggestions != null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            } else {
                TextButton(
                    enabled = !loading && url.isNotBlank(),
                    onClick = {
                        error = null
                        step = AddFeedStep.Loading
                        scope.launch {
                            runCatching { graph.feedSyncer.resolveFeedInput(url) }.fold(
                                onSuccess = { resolution ->
                                    when (resolution) {
                                        is FeedResolution.Direct -> {
                                            addFeed(resolution.url, resolution.feed.title)
                                            onDismiss()
                                        }
                                        is FeedResolution.Discovered -> {
                                            step = AddFeedStep.Suggestions(resolution.candidates)
                                        }
                                        FeedResolution.NoFeedsFound -> {
                                            step = AddFeedStep.Input
                                            error = context.getString(R.string.feed_discovery_empty)
                                        }
                                    }
                                },
                                onFailure = { throwable ->
                                    step = AddFeedStep.Input
                                    error = context.getString(
                                        if (throwable is IOException) {
                                            R.string.feed_add_network_error
                                        } else {
                                            R.string.feed_url_invalid
                                        },
                                    )
                                },
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
        dismissButton = {
            if (suggestions != null) {
                TextButton(onClick = { step = AddFeedStep.Input }) {
                    Text(stringResource(R.string.action_back))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
