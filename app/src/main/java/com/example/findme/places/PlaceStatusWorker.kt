package com.example.findme.places

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
            AutoPlaces.sync(applicationContext, owner)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() }
    }

    companion object {
        private const val NAME = "AutoPlacesStatus"
        private const val OWNER = "owner"
        private const val MAX_ATTEMPTS = 8

        fun enqueue(context: Context, owner: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PlaceStatusWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .setInputData(workDataOf(OWNER to owner)).build())
        }
    }
}
