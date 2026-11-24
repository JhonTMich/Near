package com.example.findme.places

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

/**
 * Shares the place the phone just entered or left. It runs as soon as there is network and
 * retries if the request fails; each run decides from where the phone is at that moment, so only the
 * latest transition needs to run.
 */
class PlaceStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString(OWNER) ?: return Result.failure()
        return try {
            val auth = SupabaseClientProvider.client.auth
            auth.awaitInitialization()
            // Signed out or into another account since: the transition belonged to someone else.
            if (auth.currentUserOrNull()?.id != owner) return Result.success()
            if (inputData.getBoolean(CHECK_LOCATION, false)) {
                // Without background access we cannot confirm the person is still here.
                if (!AutoPlaces.hasLocation(applicationContext) || !AutoPlaces.hasBackground(applicationContext)) return Result.success()
                if (!AutoPlaces.refreshInside(applicationContext, owner)) return Result.retry()
            }
            AutoPlaces.sync(applicationContext, owner)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() }
    }

    companion object {
        private const val NAME = "AutoPlacesStatus"
        private const val OWNER = "owner"
        private const val CHECK_LOCATION = "check_location"
        private const val PERIODIC_NAME = "AutoPlacesCheck"
        private const val MAX_ATTEMPTS = 8

        /** Repairs missed transitions and renews the place even when no widget is installed. */
        fun scheduleChecks(context: Context, owner: String?) {
            val work = WorkManager.getInstance(context)
            if (owner == null) {
                work.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            work.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PlaceStatusWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(OWNER to owner, CHECK_LOCATION to true)).build())
        }

        fun enqueue(context: Context, owner: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<PlaceStatusWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .setInputData(workDataOf(OWNER to owner)).build())
        }
    }
}
