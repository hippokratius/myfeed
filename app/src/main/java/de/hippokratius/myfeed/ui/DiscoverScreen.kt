package de.hippokratius.myfeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val categories = remember(byCategory) {
        CatalogCategory.entries.filter { byCategory[it].orEmpty().isNotEmpty() }
    }

    // Kategoriename statt Enum, damit rememberSaveable ohne eigenen Saver auskommt.
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = categories.firstOrNull { it.name == selectedName }

    val addEntry: (CatalogFeed) -> Unit = { entry ->
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
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DiscoverCategoryRow(
            categories = categories,
            selected = selectedCategory,
            onSelect = { selectedName = it?.name },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (selectedCategory == null) {
                for (category in categories) {
                    item(key = "header-${category.name}") {
                        Text(
                            text = stringResource(category.labelRes()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(byCategory.getValue(category), key = { it.url }) { entry ->
                        CatalogRow(
                            entry = entry,
                            added = FeedUrls.canonical(entry.url) in addedUrls,
                            onAdd = { addEntry(entry) },
                        )
                    }
                }
            } else {
                items(byCategory[selectedCategory].orEmpty(), key = { it.url }) { entry ->
                    CatalogRow(
                        entry = entry,
                        added = FeedUrls.canonical(entry.url) in addedUrls,
                        onAdd = { addEntry(entry) },
                    )
                }
            }
        }
    }
}

/** Horizontale Chip-Zeile "Alle" + Katalog-Kategorien – wie im Reader. */
@Composable
private fun DiscoverCategoryRow(
    categories: List<CatalogCategory>,
    selected: CatalogCategory?,
    onSelect: (CatalogCategory?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.category_all)) },
            )
        }
        items(categories, key = { it.name }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.labelRes())) },
            )
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
