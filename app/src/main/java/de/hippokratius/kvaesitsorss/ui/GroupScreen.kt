package de.hippokratius.kvaesitsorss.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.hippokratius.kvaesitsorss.AppGraph
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.core.filter.WordFilter
import de.hippokratius.kvaesitsorss.data.ArticleEntity
import de.hippokratius.kvaesitsorss.settings.AppSettings
import java.io.File
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    graph: AppGraph,
    groupId: String,
    onBack: () -> Unit,
) {
    val decodedGroupId = runCatching { URLDecoder.decode(groupId, "UTF-8") }.getOrDefault(groupId)
    val allArticles by graph.articleDao.observeGroup(decodedGroupId).collectAsState(initial = emptyList())
    val settings by graph.settingsRepository.settings.collectAsState(initial = AppSettings())
    val articles = remember(allArticles, settings.filterWords) {
        val filter = WordFilter(settings.filterWords)
        if (filter.isEmpty) allArticles else allArticles.filterNot { filter.matches(it.title) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(articles, key = { it.id }) { article ->
                ArticleRow(article)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ArticleRow(article: ArticleEntity) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                }
            }
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
        IconButton(onClick = { shareArticle(context, article) }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(R.string.action_share),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
