package de.hippokratius.kvaesitsorss.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.hippokratius.kvaesitsorss.AppGraph
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.data.ArticleEntity
import de.hippokratius.kvaesitsorss.fetch.FeedFetchWorker
import de.hippokratius.kvaesitsorss.settings.AppSettings
import de.hippokratius.kvaesitsorss.widget.WidgetEntries
import de.hippokratius.kvaesitsorss.widget.WidgetEntry
import java.io.File
import java.util.Date

/** Im Reader dürfen mehr verwandte Artikel gezeigt werden – die Reihe scrollt horizontal. */
private const val READER_MAX_RELATED = 10

/**
 * Fullscreen-Ansicht des Feeds: dieselben Inhalte wie das Widget (Einzelartikel
 * und Themen-Gruppen), aber als vollwertiger Reader in der App. Startbildschirm,
 * damit z. B. eine Kvaesitso-Wischgeste ("App öffnen") direkt hier landet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    graph: AppGraph,
    onOpenFeeds: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val context = LocalContext.current
    val feeds by graph.feedDao.observeAll().collectAsState(initial = emptyList())
    val categories by graph.feedDao.observeCategories().collectAsState(initial = emptyList())
    val settings by graph.settingsRepository.settings.collectAsState(initial = AppSettings())
    val syncRunning by FeedFetchWorker.observeSyncRunning(context).collectAsState(initial = false)

    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    // Erst wirksam, wenn die Kategorie (noch) existiert – so überlebt die
    // Auswahl das asynchrone Laden der Kategorien-Liste.
    val effectiveCategory = selectedCategory?.takeIf { it in categories }

    val articlesFlow = remember(effectiveCategory) {
        effectiveCategory.let { category ->
            if (category == null) {
                graph.articleDao.observeNewest(WidgetEntries.SOURCE_LIMIT)
            } else {
                graph.articleDao.observeNewestInCategory(category, WidgetEntries.SOURCE_LIMIT)
            }
        }
    }
    val articles by articlesFlow.collectAsState(initial = emptyList())
    val entries = remember(articles, settings.filterWords) {
        WidgetEntries.fromArticles(articles, maxRelated = READER_MAX_RELATED, filterWords = settings.filterWords)
    }
    val feedIcons = remember(feeds) {
        feeds.mapNotNull { feed -> feed.iconPath?.let { feed.id to it } }.toMap()
    }

    // Beim Öffnen aktualisieren, wenn der letzte Sync älter als das Intervall ist.
    LaunchedEffect(Unit) {
        val current = graph.settingsRepository.current()
        val stale = System.currentTimeMillis() - current.lastSyncMillis >
            current.refreshIntervalMinutes * 60_000L
        if (stale) FeedFetchWorker.syncNow(context)
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.reader_title))
                        if (settings.lastSyncMillis > 0) {
                            Text(
                                text = stringResource(
                                    R.string.widget_updated_at,
                                    DateFormat.getTimeFormat(context)
                                        .format(Date(settings.lastSyncMillis)),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { FeedFetchWorker.syncNow(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenFeeds) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.action_manage_feeds),
                        )
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
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncRunning,
            onRefresh = { FeedFetchWorker.syncNow(context) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                feeds.isEmpty() -> ReaderEmptyState(onOpenFeeds, onOpenDiscover)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (categories.isNotEmpty()) {
                        ReaderCategoryRow(
                            categories = categories,
                            selected = effectiveCategory,
                            onSelect = { selectedCategory = it },
                        )
                    }
                    if (entries.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (effectiveCategory == null) {
                                    R.string.widget_no_articles
                                } else {
                                    R.string.widget_no_articles_in_category
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(entries, key = { it.stableId }) { entry ->
                                when (entry) {
                                    is WidgetEntry.Single -> LargeArticleItem(
                                        article = entry.article,
                                        showImages = settings.showImages,
                                        iconPath = feedIcons[entry.article.feedId],
                                    )
                                    is WidgetEntry.Group -> GroupItem(
                                        group = entry,
                                        showImages = settings.showImages,
                                        feedIcons = feedIcons,
                                        onOpenGroup = onOpenGroup,
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderEmptyState(
    onOpenFeeds: () -> Unit,
    onOpenDiscover: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feeds_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            TextButton(onClick = onOpenFeeds) {
                Text(stringResource(R.string.action_manage_feeds))
            }
            TextButton(onClick = onOpenDiscover) {
                Text(stringResource(R.string.action_discover))
            }
        }
    }
}

/** Horizontale Chip-Zeile "Alle" + Kategorien – wie im Widget-Konfigurator. */
@Composable
private fun ReaderCategoryRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.category_all)) },
            )
        }
        items(categories, key = { it }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
    }
}

/**
 * Smart-Launcher-Look wie im Widget: großes Bild in voller Breite, darunter
 * Quellenzeile (Favicon + Name + relative Zeit), darunter die Überschrift.
 */
@Composable
private fun LargeArticleItem(
    article: ArticleEntity,
    showImages: Boolean,
    iconPath: String?,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openLink(context, article.link) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        val imageModel: Any? = article.imageUrl ?: article.thumbPath?.let(::File)
        if (showImages && imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        SourceLine(article, iconPath)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
        )
    }
}

/**
 * Themen-Gruppe: Hauptartikel groß, verwandte Artikel darunter als
 * horizontal scrollbare Karten-Reihe, dahinter ggf. "+N weitere Artikel".
 */
@Composable
private fun GroupItem(
    group: WidgetEntry.Group,
    showImages: Boolean,
    feedIcons: Map<Long, String>,
    onOpenGroup: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LargeArticleItem(
            article = group.main,
            showImages = showImages,
            iconPath = feedIcons[group.main.feedId],
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(group.related, key = { it.id }) { related ->
                RelatedCard(
                    article = related,
                    showImages = showImages,
                    iconPath = feedIcons[related.feedId],
                )
            }
        }
        if (group.extraCount > 0) {
            TextButton(
                onClick = { onOpenGroup(group.groupId) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.widget_more_articles, group.extraCount))
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** Kompakte Karte eines verwandten Artikels für die horizontale Reihe. */
@Composable
private fun RelatedCard(
    article: ArticleEntity,
    showImages: Boolean,
    iconPath: String?,
) {
    val context = LocalContext.current
    Surface(
        onClick = { openLink(context, article.link) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(280.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // Quellenname bekommt die volle Kartenbreite, damit z. B.
                    // "DER STANDARD" nicht abgeschnitten wird.
                    SourceBadge(article, iconPath)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        minLines = 3,
                        maxLines = 3,
                    )
                }
                if (showImages && article.thumbPath != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    AsyncImage(
                        model = File(article.thumbPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = relativeTime(article),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                ShareIcon(article, tint = color)
            }
        }
    }
}

/** Quellenzeile: kleines Feed-Logo (falls vorhanden), Name links, Zeit und Teilen rechts. */
@Composable
private fun SourceLine(article: ArticleEntity, iconPath: String?) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(article, iconPath, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = relativeTime(article),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(4.dp))
        ShareIcon(article, tint = color)
    }
}

/** Feed-Logo (falls vorhanden) plus Quellenname mit Ellipse bei Platzmangel. */
@Composable
private fun SourceBadge(
    article: ArticleEntity,
    iconPath: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (iconPath != null) {
            AsyncImage(
                model = File(iconPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(16.dp).clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = article.sourceTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Runder Teilen-Button, kompakt genug für die Quellenzeile. */
@Composable
private fun ShareIcon(article: ArticleEntity, tint: Color) {
    val context = LocalContext.current
    Icon(
        imageVector = Icons.Default.Share,
        contentDescription = stringResource(R.string.action_share),
        tint = tint,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { shareArticle(context, article) }
            .padding(6.dp)
            .size(20.dp),
    )
}

private fun relativeTime(article: ArticleEntity): String =
    DateUtils.getRelativeTimeSpanString(
        article.publishedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

private fun openLink(context: android.content.Context, link: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    }
}

/** Öffnet das System-Share-Sheet mit Titel und Link des Artikels. */
internal fun shareArticle(context: android.content.Context, article: ArticleEntity) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
