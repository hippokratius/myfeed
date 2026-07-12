package de.hippokratius.kvaesitsorss.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hippokratius.kvaesitsorss.AppGraph
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.core.catalog.CatalogCategory
import de.hippokratius.kvaesitsorss.core.catalog.CatalogFeed
import de.hippokratius.kvaesitsorss.core.catalog.FeedCatalog
import de.hippokratius.kvaesitsorss.core.catalog.FeedUrls
import de.hippokratius.kvaesitsorss.data.FeedEntity
import de.hippokratius.kvaesitsorss.fetch.FeedFetchWorker
import kotlinx.coroutines.launch

/** Kuratierte Feed-Vorschläge, nach Kategorie gruppiert, mit Ein-Tipp-Hinzufügen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feeds by graph.feedDao.observeAll().collectAsState(initial = emptyList())
    val addedUrls = remember(feeds) {
        feeds.map { FeedUrls.canonical(it.url) }.toSet()
    }
    val byCategory = remember { FeedCatalog.byCategory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_discover)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            for (category in CatalogCategory.entries) {
                val entries = byCategory[category].orEmpty()
                if (entries.isEmpty()) continue
                item(key = "header-${category.name}") {
                    Text(
                        text = stringResource(category.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(entries, key = { it.url }) { entry ->
                    CatalogRow(
                        entry = entry,
                        added = FeedUrls.canonical(entry.url) in addedUrls,
                        onAdd = {
                            scope.launch {
                                graph.feedDao.insert(
                                    FeedEntity(
                                        url = entry.url,
                                        title = entry.title,
                                        category = context.getString(entry.category.labelRes()),
                                    ),
                                )
                                FeedFetchWorker.syncNow(context)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    entry: CatalogFeed,
    added: Boolean,
    onAdd: () -> Unit,
) {
    val languageRes = if (entry.language == "de") R.string.discover_language_de else R.string.discover_language_en
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = "${stringResource(languageRes)} · ${entry.url}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (added) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.discover_added),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    }
}
