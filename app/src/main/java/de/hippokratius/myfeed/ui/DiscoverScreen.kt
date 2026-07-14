package de.hippokratius.myfeed.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hippokratius.myfeed.AppGraph
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.core.catalog.CatalogCategory
import de.hippokratius.myfeed.core.catalog.CatalogFeed
import de.hippokratius.myfeed.core.catalog.FeedCatalog
import de.hippokratius.myfeed.core.catalog.FeedUrls
import de.hippokratius.myfeed.data.FeedEntity
import de.hippokratius.myfeed.fetch.FeedFetchWorker
import kotlinx.coroutines.launch

/**
 * Kuratierte Feed-Vorschläge, nach Kategorie gruppiert, mit Ein-Tipp-Hinzufügen.
 * Wird als Tab-Inhalt im Quellen-Screen eingebettet (siehe [FeedsScreen]).
 */
@Composable
fun DiscoverTab(graph: AppGraph) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feeds by graph.feedDao.observeAll().collectAsState(initial = emptyList())
    val addedUrls = remember(feeds) {
        feeds.map { FeedUrls.canonical(it.url) }.toSet()
    }
    val byCategory = remember { FeedCatalog.byCategory() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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

@Composable
private fun CatalogRow(
    entry: CatalogFeed,
    added: Boolean,
    onAdd: () -> Unit,
) {
    val languageRes = if (entry.language == "de") R.string.discover_language_de else R.string.discover_language_en
    FeedSuggestionRow(
        title = entry.title,
        subtitle = "${stringResource(languageRes)} · ${entry.url}",
        added = added,
        onAdd = onAdd,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
