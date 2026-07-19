package de.hippokratius.myfeed.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.hippokratius.myfeed.AppGraph
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.data.ArticleEntity
import de.hippokratius.myfeed.fetch.FeedFetchWorker
import de.hippokratius.myfeed.settings.AppSettings
import de.hippokratius.myfeed.widget.WidgetEntries
import de.hippokratius.myfeed.widget.WidgetEntry
import de.hippokratius.myfeed.widget.articles
import java.io.File
import java.util.Date
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Im Reader dürfen mehr verwandte Artikel gezeigt werden – die Reihe scrollt horizontal. */
private const val READER_MAX_RELATED = 10

/** Deckkraft, mit der gelesene Artikel ausgegraut dargestellt werden. */
private const val READ_ALPHA = 0.45f

/**
 * Fullscreen-Ansicht des Feeds: dieselben Inhalte wie das Widget (Einzelartikel
 * und Themen-Gruppen), aber als vollwertiger Reader in der App. Startbildschirm –
 * die App öffnet direkt im Vollbild-Feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    graph: AppGraph,
    onOpenFeeds: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSaved: () -> Unit,
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

    // Ohne Limit: Der Reader zeigt alle Artikel innerhalb der Aufbewahrungsdauer.
    // Nur wegen Archiv/Lesezeichen länger aufbewahrte Artikel bleiben außen vor.
    val articlesFlow = remember(effectiveCategory, settings.maxAgeDays) {
        val cutoff = settings.feedCutoffMillis(System.currentTimeMillis())
        effectiveCategory.let { category ->
            if (category == null) {
                graph.articleDao.observeAllNewest(cutoff)
            } else {
                graph.articleDao.observeAllNewestInCategory(category, cutoff)
            }
        }
    }
    val articles by articlesFlow.collectAsState(initial = emptyList())
    val entries = remember(articles, settings.filterWords) {
        WidgetEntries.fromArticles(
            articles,
            maxRelated = READER_MAX_RELATED,
            filterWords = settings.filterWords,
            maxEntries = Int.MAX_VALUE,
        )
    }

    // In dieser Sitzung gelesene Artikel bleiben trotz "Gelesene ausblenden"
    // sichtbar, damit die Liste beim Scrollen nicht unter dem Finger springt.
    var sessionReadIds by remember { mutableStateOf(setOf<Long>()) }
    val visibleEntries = remember(entries, settings.hideRead, sessionReadIds) {
        if (!settings.hideRead) {
            entries
        } else {
            entries.filter { entry ->
                entry.articles().any { !it.isRead || it.id in sessionReadIds }
            }
        }
    }
    val feedIcons = remember(feeds) {
        feeds.mapNotNull { feed -> feed.iconPath?.let { feed.id to it } }.toMap()
    }

    val onOpenArticle: (ArticleEntity) -> Unit = { article ->
        openArticleAndArchive(context, graph, article)
    }
    val onToggleBookmark: (ArticleEntity) -> Unit = { article ->
        toggleBookmark(graph, article)
    }

    // Beim Öffnen aktualisieren, wenn der letzte Sync älter als das Intervall ist.
    LaunchedEffect(Unit) {
        val current = graph.settingsRepository.current()
        val stale = System.currentTimeMillis() - current.lastSyncMillis >
            current.refreshIntervalMinutes * 60_000L
        if (stale) FeedFetchWorker.syncNow(context)
    }

    // Scroll-Zustand der Artikelliste; sichtbar gemacht für den "Zum Anfang"-FAB.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // Beim Kategoriewechsel nach oben springen, damit der Lese-Tracker die
    // alte Scroll-Position nicht auf die neue Liste anwendet.
    LaunchedEffect(effectiveCategory) {
        listState.scrollToItem(0)
    }

    // Lese-Tracking: Einträge, die komplett nach oben aus dem Bild gescrollt
    // wurden (von oben nach unten überscrollt), gelten als gelesen. Geschrieben
    // wird erst bei Scroll-Pause, damit DB-Updates (und die daraus folgenden
    // Listen-Neuberechnungen) nicht mitten im Fling passieren.
    LaunchedEffect(visibleEntries) {
        snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, firstVisible) ->
                if (firstVisible <= 0) return@collect
                val ids = visibleEntries.take(firstVisible)
                    .flatMap { it.articles() }
                    .filter { !it.isRead && it.id !in sessionReadIds }
                    .map { it.id }
                if (ids.isNotEmpty()) {
                    sessionReadIds = sessionReadIds + ids
                    graph.articleDao.markRead(ids, System.currentTimeMillis())
                }
            }
    }

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
                    IconButton(
                        onClick = {
                            scope.launch { graph.settingsRepository.setHideRead(!settings.hideRead) }
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (settings.hideRead) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            ),
                            contentDescription = stringResource(
                                if (settings.hideRead) R.string.action_show_read else R.string.action_hide_read,
                            ),
                        )
                    }
                    IconButton(onClick = onOpenSaved) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookmark),
                            contentDescription = stringResource(R.string.saved_title),
                        )
                    }
                    IconButton(onClick = { FeedFetchWorker.syncNow(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenFeeds) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.action_manage_feeds),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (showScrollToTop) {
                FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_scroll_to_top),
                    )
                }
            }
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
                    if (visibleEntries.isEmpty()) {
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
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(visibleEntries, key = { it.stableId }) { entry ->
                                when (entry) {
                                    is WidgetEntry.Single -> LargeArticleItem(
                                        article = entry.article,
                                        showImages = settings.showImages,
                                        iconPath = feedIcons[entry.article.feedId],
                                        onOpen = onOpenArticle,
                                        onToggleBookmark = onToggleBookmark,
                                    )
                                    is WidgetEntry.Group -> GroupItem(
                                        group = entry,
                                        showImages = settings.showImages,
                                        feedIcons = feedIcons,
                                        onOpenGroup = onOpenGroup,
                                        onOpen = onOpenArticle,
                                        onToggleBookmark = onToggleBookmark,
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
    onOpen: (ArticleEntity) -> Unit,
    onToggleBookmark: (ArticleEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(article) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (article.isRead) READ_ALPHA else 1f),
    ) {
        // Lokales, kleines Thumbnail bevorzugen; das Remote-Bild lädt Coil nur
        // als Fallback – lazy beim Scrollen und mit Memory-/Disk-Cache.
        val imageModel: Any? = article.thumbPath?.let(::File) ?: article.imageUrl
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
        SourceLine(article, iconPath, onToggleBookmark)
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
    onOpen: (ArticleEntity) -> Unit,
    onToggleBookmark: (ArticleEntity) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LargeArticleItem(
            article = group.main,
            showImages = showImages,
            iconPath = feedIcons[group.main.feedId],
            onOpen = onOpen,
            onToggleBookmark = onToggleBookmark,
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
                    onOpen = onOpen,
                    onToggleBookmark = onToggleBookmark,
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
    onOpen: (ArticleEntity) -> Unit,
    onToggleBookmark: (ArticleEntity) -> Unit,
) {
    Surface(
        onClick = { onOpen(article) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(280.dp).alpha(if (article.isRead) READ_ALPHA else 1f),
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
                val thumbModel: Any? = article.thumbPath?.let(::File) ?: article.imageUrl
                if (showImages && thumbModel != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    AsyncImage(
                        model = thumbModel,
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
                BookmarkIcon(article, tint = color, onToggleBookmark = onToggleBookmark)
                ShareIcon(article, tint = color)
            }
        }
    }
}

/** Quellenzeile: Feed-Logo und Name links, Zeit, Lesezeichen und Teilen rechts. */
@Composable
private fun SourceLine(
    article: ArticleEntity,
    iconPath: String?,
    onToggleBookmark: (ArticleEntity) -> Unit,
) {
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
        BookmarkIcon(article, tint = color, onToggleBookmark = onToggleBookmark)
        ShareIcon(article, tint = color)
    }
}

/** Lesezeichen-Umschalter, gleiche Kompaktform wie [ShareIcon]. */
@Composable
internal fun BookmarkIcon(
    article: ArticleEntity,
    tint: Color,
    onToggleBookmark: (ArticleEntity) -> Unit,
) {
    Icon(
        painter = painterResource(
            if (article.isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border,
        ),
        contentDescription = stringResource(
            if (article.isBookmarked) R.string.action_bookmark_remove else R.string.action_bookmark_add,
        ),
        tint = if (article.isBookmarked) MaterialTheme.colorScheme.primary else tint,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onToggleBookmark(article) }
            .padding(6.dp)
            .size(20.dp),
    )
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

/**
 * Öffnet den Artikel im Browser und markiert ihn als archiviert – geöffnete
 * Artikel bleiben so über die Lebensdauer des Feeds hinaus im Archiv auffindbar.
 */
internal fun openArticleAndArchive(
    context: android.content.Context,
    graph: AppGraph,
    article: ArticleEntity,
) {
    graph.applicationScope.launch {
        graph.articleDao.markArchived(article.id, System.currentTimeMillis())
    }
    openLink(context, article.link)
}

/** Setzt oder entfernt das Lesezeichen eines Artikels. */
internal fun toggleBookmark(graph: AppGraph, article: ArticleEntity) {
    graph.applicationScope.launch {
        graph.articleDao.setBookmarked(
            article.id,
            if (article.isBookmarked) null else System.currentTimeMillis(),
        )
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
