package com.example.findme.places

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Decides «En la calle» again when an activity ends: during it the schedule was shown, and afterwards
 * the person may still be outside every saved place. A minute late is fine, so WorkManager is enough.
 */
class StreetStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { AutoPlaces.reconcile(applicationContext, fromWorker = true) }
        return Result.success()
    }

    companion object {
        private const val NAME = "AutoPlacesStreetStatus"

        /** From the worker itself the next run is appended, so replacing does not cancel the run in progress. */
        fun schedule(context: Context, at: Instant?, fromWorker: Boolean = false) {
            if (at == null) { if (!fromWorker) cancel(context); return }
            val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(1_000)
            WorkManager.getInstance(context).enqueueUniqueWork(NAME,
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<StreetStatusWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
    }
}
