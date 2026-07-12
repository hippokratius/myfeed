package de.hippokratius.kvaesitsorss.fetch

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.hippokratius.kvaesitsorss.KvaesitsoRssApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FeedFetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KvaesitsoRssApp
        return try {
            app.graph.feedSyncer.syncAll()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "feed-sync-periodic"
        private const val ONE_TIME_WORK_NAME = "feed-sync-now"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context, intervalMinutes: Int) {
            val interval = intervalMinutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<FeedFetchWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FeedFetchWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** Läuft gerade ein manuell angestoßener Sync? (Für Pull-to-Refresh in der App.) */
        fun observeSyncRunning(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK_NAME)
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
    }
}
