package de.hippokratius.myfeed

import android.app.Application
import android.content.Context
import de.hippokratius.myfeed.data.AppDatabase
import de.hippokratius.myfeed.fetch.FeedFetchWorker
import de.hippokratius.myfeed.fetch.FeedSyncer
import de.hippokratius.myfeed.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Einfache, handverdrahtete Abhängigkeiten – für die App-Größe völlig ausreichend. */
class AppGraph(context: Context) {
    val database = AppDatabase.create(context)
    val feedDao = database.feedDao()
    val articleDao = database.articleDao()
    val settingsRepository = SettingsRepository(context)
    val feedSyncer = FeedSyncer(context, feedDao, articleDao, settingsRepository)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class MyFeedApp : Application() {

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
}
