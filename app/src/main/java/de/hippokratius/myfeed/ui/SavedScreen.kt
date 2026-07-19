package de.hippokratius.myfeed.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.hippokratius.myfeed.AppGraph
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.data.ArticleEntity
import java.io.File

/**
 * Extralisten für gemerkte und geöffnete Artikel: Der Lesezeichen-Tab zeigt
 * alle mit Lesezeichen versehenen Artikel, der Archiv-Tab alle jemals
 * geöffneten. Beide überleben die normale Aufbewahrungsdauer des Feeds
 * (einstellbar in den Einstellungen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val bookmarked by graph.articleDao.observeBookmarked().collectAsState(initial = emptyList())
    val archived by graph.articleDao.observeArchived().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_title)) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_bookmarks)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_archive)) },
                )
            }
            val articles = if (selectedTab == 0) bookmarked else archived
            if (articles.isEmpty()) {
                Text(
                    text = stringResource(
                        if (selectedTab == 0) {
                            R.string.saved_empty_bookmarks
                        } else {
                            R.string.saved_empty_archive
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(articles, key = { it.id }) { article ->
                        SavedArticleRow(article, graph)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedArticleRow(article: ArticleEntity, graph: AppGraph) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openArticleAndArchive(context, graph, article) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                article.publishedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            Text(
                text = "${article.sourceTitle} · $relativeTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (article.thumbPath != null) {
            AsyncImage(
                model = File(article.thumbPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        BookmarkIcon(
            article = article,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onToggleBookmark = { toggleBookmark(graph, it) },
        )
        IconButton(onClick = { shareArticle(context, article) }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.action_share),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
