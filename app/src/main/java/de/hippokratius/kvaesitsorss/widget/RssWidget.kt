package de.hippokratius.kvaesitsorss.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.hippokratius.kvaesitsorss.KvaesitsoRssApp
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.data.ArticleEntity
import de.hippokratius.kvaesitsorss.fetch.FeedFetchWorker
import de.hippokratius.kvaesitsorss.ui.MainActivity
import java.util.Date

class RssWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RssWidget()
}

/** Vorab geladene Bitmaps für ein Rendering (RemoteViews-Speicher ist knapp). */
private class WidgetBitmaps(
    val articleImages: Map<Long, Bitmap>,
    val relatedThumbs: Map<Long, Bitmap>,
    val feedIcons: Map<Long, Bitmap>,
)

class RssWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as KvaesitsoRssApp
        // Pro Widget-Instanz konfigurierte Kategorie; fehlt der Key, zeigt das Widget alles.
        val category = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_CATEGORY]
        val data = WidgetEntries.buildData(app.graph.articleDao, app.graph.feedDao, category)
        val settings = app.graph.settingsRepository.current()
        val bitmaps = if (settings.showImages) {
            loadBitmaps(data)
        } else {
            WidgetBitmaps(emptyMap(), emptyMap(), loadFeedIcons(data))
        }
        val hasFeeds = if (category == null) {
            app.graph.feedDao.count() > 0
        } else {
            app.graph.feedDao.countInCategory(category) > 0
        }

        provideContent {
            GlanceTheme {
                WidgetRoot(data.entries, bitmaps, hasFeeds, settings.lastSyncMillis, category)
            }
        }
    }

    private fun loadBitmaps(data: WidgetData): WidgetBitmaps {
        val articleImages = mutableMapOf<Long, Bitmap>()
        val relatedThumbs = mutableMapOf<Long, Bitmap>()

        // Große Artikelbilder: Gruppen-Hauptartikel und neueste Einzelartikel zuerst.
        for (entry in data.entries) {
            if (articleImages.size >= MAX_ARTICLE_BITMAPS) break
            val article = when (entry) {
                is WidgetEntry.Single -> entry.article
                is WidgetEntry.Group -> entry.main
            }
            decode(article.thumbPath)?.let { articleImages[article.id] = it }
        }

        // Kleine Thumbnails für verwandte Artikel der obersten Gruppen.
        for (entry in data.entries.filterIsInstance<WidgetEntry.Group>().take(MAX_GROUPS_WITH_THUMBS)) {
            for (related in entry.related) {
                if (relatedThumbs.size >= MAX_RELATED_BITMAPS) break
                decode(related.thumbPath)?.let { relatedThumbs[related.id] = it }
            }
        }

        return WidgetBitmaps(articleImages, relatedThumbs, loadFeedIcons(data))
    }

    private fun loadFeedIcons(data: WidgetData): Map<Long, Bitmap> {
        val icons = mutableMapOf<Long, Bitmap>()
        for ((feedId, path) in data.feedIconPaths) {
            if (icons.size >= MAX_FEED_ICONS) break
            decode(path)?.let { icons[feedId] = it }
        }
        return icons
    }

    private fun decode(path: String?): Bitmap? {
        if (path == null) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    companion object {
        private const val MAX_ARTICLE_BITMAPS = 14
        private const val MAX_RELATED_BITMAPS = 12
        private const val MAX_GROUPS_WITH_THUMBS = 4
        private const val MAX_FEED_ICONS = 20

        /** Pro-Instanz-State: Kategorie-Filter dieses Widgets (fehlt = alle). */
        val KEY_CATEGORY = stringPreferencesKey("widget_category")

        suspend fun updateAll(context: Context) {
            RssWidget().updateAll(context)
        }
    }
}

// ---- Composables ----

@Composable
private fun WidgetRoot(
    entries: List<WidgetEntry>,
    bitmaps: WidgetBitmaps,
    hasFeeds: Boolean,
    lastSyncMillis: Long,
    category: String?,
) {
    var root = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .appWidgetBackground()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        root = root.cornerRadius(20.dp)
    }
    Column(modifier = root.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Header(lastSyncMillis, category)
        when {
            !hasFeeds && category != null -> EmptyHint(R.string.widget_no_articles_in_category)
            !hasFeeds -> EmptyHint(R.string.widget_no_feeds)
            entries.isEmpty() && category != null -> EmptyHint(R.string.widget_no_articles_in_category)
            entries.isEmpty() -> EmptyHint(R.string.widget_no_articles)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries, itemId = { it.itemId() }) { entry ->
                    when (entry) {
                        is WidgetEntry.Single -> Column(modifier = GlanceModifier.fillMaxWidth()) {
                            LargeArticle(entry.article, bitmaps.articleImages[entry.article.id], bitmaps.feedIcons)
                            EntryDivider()
                        }
                        is WidgetEntry.Group -> Column(modifier = GlanceModifier.fillMaxWidth()) {
                            GroupBlock(entry, bitmaps)
                            EntryDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun WidgetEntry.itemId(): Long = when (this) {
    is WidgetEntry.Single -> article.id
    is WidgetEntry.Group -> -(main.id + 1)
}

@Composable
private fun Header(lastSyncMillis: Long, category: String?) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight().clickable(openAppAction(context))) {
            Text(
                text = context.getString(R.string.widget_label) +
                    (category?.let { " · $it" } ?: ""),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (lastSyncMillis > 0) {
                Text(
                    text = context.getString(
                        R.string.widget_updated_at,
                        DateFormat.getTimeFormat(context).format(Date(lastSyncMillis)),
                    ),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = context.getString(R.string.action_refresh),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier
                .size(24.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

@Composable
private fun EmptyHint(textRes: Int) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(openAppAction(context)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(textRes),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

/**
 * Smart-Launcher-Look: großes Bild in voller Breite, darunter Quellenzeile
 * (Favicon + Name + relative Zeit), darunter die Überschrift groß und fett.
 */
@Composable
private fun LargeArticle(
    article: ArticleEntity,
    image: Bitmap?,
    feedIcons: Map<Long, Bitmap>,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(openArticleAction(article.link)),
    ) {
        if (image != null) {
            var imageModifier = GlanceModifier.fillMaxWidth().height(160.dp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                imageModifier = imageModifier.cornerRadius(16.dp)
            }
            Image(
                provider = ImageProvider(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
            )
            Spacer(modifier = GlanceModifier.height(10.dp))
        }
        SourceLine(article, feedIcons)
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = article.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 3,
        )
    }
}

/** Gruppe: Hauptartikel groß, verwandte Artikel als kompakte Unter-Karten. */
@Composable
private fun GroupBlock(group: WidgetEntry.Group, bitmaps: WidgetBitmaps) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        LargeArticle(group.main, bitmaps.articleImages[group.main.id], bitmaps.feedIcons)

        for (related in group.related) {
            RelatedCard(related, bitmaps.relatedThumbs[related.id], bitmaps.feedIcons)
            Spacer(modifier = GlanceModifier.height(8.dp))
        }

        if (group.extraCount > 0) {
            Text(
                text = context.getString(R.string.widget_more_articles, group.extraCount),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier
                    .padding(vertical = 4.dp)
                    .clickable(
                        actionStartActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_GROUP_ID, group.groupId),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun RelatedCard(
    article: ArticleEntity,
    thumb: Bitmap?,
    feedIcons: Map<Long, Bitmap>,
) {
    var card = GlanceModifier
        .fillMaxWidth()
        .background(GlanceTheme.colors.surfaceVariant)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        card = card.cornerRadius(12.dp)
    }
    Row(
        modifier = card.padding(12.dp).clickable(openArticleAction(article.link)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            SourceLine(article, feedIcons, onVariant = true)
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = article.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 3,
            )
        }
        if (thumb != null) {
            Spacer(modifier = GlanceModifier.width(10.dp))
            var thumbModifier = GlanceModifier.size(64.dp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                thumbModifier = thumbModifier.cornerRadius(8.dp)
            }
            Image(
                provider = ImageProvider(thumb),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = thumbModifier,
            )
        }
    }
}

/** Quellenzeile: kleines Feed-Logo (falls vorhanden), Name links, Zeit rechts. */
@Composable
private fun SourceLine(
    article: ArticleEntity,
    feedIcons: Map<Long, Bitmap>,
    onVariant: Boolean = false,
) {
    val color = GlanceTheme.colors.onSurfaceVariant
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        article.publishedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = feedIcons[article.feedId]
        if (icon != null) {
            var iconModifier = GlanceModifier.size(16.dp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                iconModifier = iconModifier.cornerRadius(8.dp)
            }
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = iconModifier,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        Text(
            text = article.sourceTitle,
            style = TextStyle(color = color, fontSize = 11.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = relativeTime,
            style = TextStyle(color = color, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun EntryDivider() {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.surfaceVariant),
    )
}

private fun openArticleAction(link: String) =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

private fun openAppAction(context: Context) =
    actionStartActivity(Intent(context, MainActivity::class.java))

/** Manueller Refresh über den Button im Widget-Header. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        FeedFetchWorker.syncNow(context)
    }
}
