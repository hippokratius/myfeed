package de.hippokratius.kvaesitsorss.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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

class RssWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RssWidget()
}

class RssWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as KvaesitsoRssApp
        val entries = WidgetEntries.build(app.graph.articleDao)
        val showImages = app.graph.settingsRepository.current().showImages
        val bitmaps = if (showImages) loadBitmaps(entries) else emptyMap()
        val hasFeeds = app.graph.feedDao.count() > 0

        provideContent {
            GlanceTheme {
                WidgetRoot(entries, bitmaps, hasFeeds)
            }
        }
    }

    /** Bitmaps vorab laden – begrenzt, da RemoteViews-Speicher knapp ist. */
    private fun loadBitmaps(entries: List<WidgetEntry>): Map<Long, Bitmap> {
        val result = mutableMapOf<Long, Bitmap>()
        for (entry in entries) {
            if (result.size >= MAX_BITMAPS) break
            val article = when (entry) {
                is WidgetEntry.Single -> entry.article
                is WidgetEntry.Group -> entry.main
            }
            val path = article.thumbPath ?: continue
            val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: continue
            result[article.id] = bitmap
        }
        return result
    }

    companion object {
        private const val MAX_BITMAPS = 10

        suspend fun updateAll(context: Context) {
            RssWidget().updateAll(context)
        }
    }
}

// ---- Composables ----

@Composable
private fun WidgetRoot(
    entries: List<WidgetEntry>,
    bitmaps: Map<Long, Bitmap>,
    hasFeeds: Boolean,
) {
    var root = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .appWidgetBackground()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        root = root.cornerRadius(20.dp)
    }
    Column(modifier = root.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Header()
        when {
            !hasFeeds -> EmptyHint(R.string.widget_no_feeds)
            entries.isEmpty() -> EmptyHint(R.string.widget_no_articles)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries, itemId = { it.itemId() }) { entry ->
                    when (entry) {
                        is WidgetEntry.Single -> SingleArticleRow(entry.article, bitmaps[entry.article.id])
                        is WidgetEntry.Group -> GroupCard(entry, bitmaps[entry.main.id])
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
private fun Header() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_label),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        )
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = context.getString(R.string.action_refresh),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
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
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(textRes),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun SingleArticleRow(article: ArticleEntity, bitmap: Bitmap?) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(openArticleAction(article.link)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = article.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 3,
                )
                MetaLine(article)
            }
            if (bitmap != null) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Thumbnail(bitmap, sizeDp = 56)
            }
        }
        Divider()
    }
}

@Composable
private fun GroupCard(group: WidgetEntry.Group, mainBitmap: Bitmap?) {
    val context = LocalContext.current
    var card = GlanceModifier
        .fillMaxWidth()
        .background(GlanceTheme.colors.secondaryContainer)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        card = card.cornerRadius(12.dp)
    }
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = card.padding(10.dp)) {
            // Hauptartikel: Bild oben, große Überschrift darunter.
            Column(modifier = GlanceModifier.fillMaxWidth().clickable(openArticleAction(group.main.link))) {
                if (mainBitmap != null) {
                    var image = GlanceModifier.fillMaxWidth().height(110.dp)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        image = image.cornerRadius(8.dp)
                    }
                    Image(
                        provider = ImageProvider(mainBitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = image,
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
                Text(
                    text = group.main.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 3,
                )
                MetaLine(group.main, onContainer = true)
            }

            // Verwandte Artikel, kompakt und einzeln antippbar.
            for (related in group.related) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Column(modifier = GlanceModifier.fillMaxWidth().clickable(openArticleAction(related.link))) {
                    Text(
                        text = related.title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 13.sp,
                        ),
                        maxLines = 2,
                    )
                    MetaLine(related, onContainer = true)
                }
            }

            if (group.extraCount > 0) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = context.getString(R.string.widget_more_articles, group.extraCount),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_GROUP_ID, group.groupId),
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetaLine(article: ArticleEntity, onContainer: Boolean = false) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        article.publishedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    Text(
        text = "${article.sourceTitle} · $relativeTime",
        style = TextStyle(
            color = if (onContainer) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurfaceVariant,
            fontSize = 11.sp,
        ),
        maxLines = 1,
    )
}

@Composable
private fun Thumbnail(bitmap: Bitmap, sizeDp: Int) {
    var modifier = GlanceModifier.size(sizeDp.dp)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        modifier = modifier.cornerRadius(8.dp)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun Divider() {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.surfaceVariant),
    )
}

private fun openArticleAction(link: String) =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

/** Manueller Refresh über den Button im Widget-Header. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        FeedFetchWorker.syncNow(context)
    }
}
