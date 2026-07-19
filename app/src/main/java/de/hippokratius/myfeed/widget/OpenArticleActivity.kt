package de.hippokratius.myfeed.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.hippokratius.myfeed.MyFeedApp
import kotlinx.coroutines.launch

/**
 * Unsichtbares Trampolin für Artikel-Taps aus dem Widget: markiert den Artikel
 * als archiviert (geöffnet) und reicht den Link an den Browser weiter. So
 * landen auch im Widget geöffnete Artikel im Archiv der App.
 */
class OpenArticleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val articleId = intent.getLongExtra(EXTRA_ARTICLE_ID, -1L)
        val link = intent.getStringExtra(EXTRA_LINK)

        if (articleId >= 0) {
            val graph = (application as MyFeedApp).graph
            graph.applicationScope.launch {
                graph.articleDao.markArchived(articleId, System.currentTimeMillis())
            }
        }
        if (!link.isNullOrBlank()) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
        }
        finish()
    }

    companion object {
        private const val EXTRA_ARTICLE_ID = "de.hippokratius.myfeed.extra.ARTICLE_ID"
        private const val EXTRA_LINK = "de.hippokratius.myfeed.extra.LINK"

        fun intent(context: Context, articleId: Long, link: String): Intent =
            Intent(context, OpenArticleActivity::class.java)
                .putExtra(EXTRA_ARTICLE_ID, articleId)
                .putExtra(EXTRA_LINK, link)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
