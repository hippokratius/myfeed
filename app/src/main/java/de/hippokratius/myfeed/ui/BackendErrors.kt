package de.hippokratius.myfeed.ui

import android.content.Context
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.backend.BackendException

/** Nutzerlesbarer Text für Fehler aus Backend-Operationen (Toast/Dialog). */
internal fun backendErrorText(context: Context, error: Throwable): String = when (error) {
    is BackendException.NotConnected -> context.getString(R.string.nextcloud_auth_required)
    is BackendException.AuthLost -> context.getString(R.string.nextcloud_auth_required)
    is BackendException.NewsAppMissing -> context.getString(R.string.nextcloud_news_app_missing)
    is BackendException.FeedRejected -> context.getString(R.string.nextcloud_feed_rejected)
    is BackendException.ServerUnreachable -> context.getString(R.string.nextcloud_server_unreachable)
    else -> context.getString(R.string.feed_add_network_error)
}
