package de.hippokratius.myfeed

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import de.hippokratius.myfeed.backend.BackendRegistry
import de.hippokratius.myfeed.backend.LocalRssBackend
import de.hippokratius.myfeed.data.AppDatabase
import de.hippokratius.myfeed.fetch.FeedFetchWorker
import de.hippokratius.myfeed.fetch.FeedSyncer
import de.hippokratius.myfeed.fetch.SyncPostProcessor
import de.hippokratius.myfeed.fetch.ThumbnailStore
import de.hippokratius.myfeed.nextcloud.NextcloudNewsBackend
import de.hippokratius.myfeed.nextcloud.SsoNewsApi
import de.hippokratius.myfeed.nextcloud.SsoSessionManager
import de.hippokratius.myfeed.settings.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Einfache, handverdrahtete Abhängigkeiten – für die App-Größe völlig ausreichend. */
class AppGraph(context: Context) {
    val database = AppDatabase.create(context)
    val feedDao = database.feedDao()
    val articleDao = database.articleDao()
    val settingsRepository = SettingsRepository(context)

    /** Gemeinsamer HTTP-Client für RSS-Fetching, Thumbnails und Coil. */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val thumbnailStore = ThumbnailStore(context, httpClient)
    val feedSyncer = FeedSyncer(feedDao, articleDao, httpClient, thumbnailStore)
    val syncPostProcessor =
        SyncPostProcessor(context, feedDao, articleDao, settingsRepository, thumbnailStore)

    val ssoSessionManager = SsoSessionManager(context, settingsRepository)
    val nextcloudNewsBackend = NextcloudNewsBackend(
        api = SsoNewsApi(ssoSessionManager),
        feedDao = feedDao,
        articleDao = articleDao,
        settingsRepository = settingsRepository,
        thumbnailStore = thumbnailStore,
        postProcessor = syncPostProcessor,
    )
    val localRssBackend = LocalRssBackend(feedSyncer, feedDao, articleDao, syncPostProcessor)
    val backendRegistry = BackendRegistry(settingsRepository, localRssBackend, nextcloudNewsBackend)

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class MyFeedApp : Application(), ImageLoaderFactory {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)

        // Periodischen Sync einplanen und bei Intervall-Änderungen neu planen.
        graph.applicationScope.launch {
            graph.settingsRepository.settings
                .map { it.refreshIntervalMinutes }
                .distinctUntilChanged()
                .collect { minutes ->
                    FeedFetchWorker.schedulePeriodic(this@MyFeedApp, minutes)
                }
        }
    }

    /**
     * App-weiter Coil-Loader: nutzt den gemeinsamen OkHttp-Client (gemeinsamer
     * Verbindungs-Pool) und sendet unseren User-Agent – manche Seiten
     * (z. B. 9to5linux.com) beantworten den OkHttp-Standard-UA mit 403.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                graph.httpClient.newBuilder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", FeedSyncer.USER_AGENT)
                                .build(),
                        )
                    }
                    .build()
            }
            .build()
}
