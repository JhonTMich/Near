package com.example.findme.attendance

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.model.ActivityAttendance
import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.data.repository.AccountRepository
import com.example.findme.places.AutoPlaces
import com.example.findme.widget.WidgetStore
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Marks attended the activities whose place the phone is in: on arriving at a saved place and when the
 * window of the next activity opens. It reads the cached schedule, so it works offline; the mark is sent
 * by WorkManager as soon as there is network.
 */
object AttendanceTracker {
    suspend fun check(context: Context, fromWorker: Boolean = false) {
        val data = WidgetStore.data(context) ?: return
        val owner = data.owner
        val settings = AccountSettingsRepository(context).settings(owner).first()
        if (!settings.attendance) { if (!fromWorker) AttendanceCheckWorker.cancel(context); return }
        val zone = data.profiles.find { it.id == owner }?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val own = data.schedules.filter { it.userId == owner }
        val places = AutoPlaces.places(context, owner)
        val since = settings.attendanceSince
        Attendance.dueNow(own, places, AutoPlaces.inside(context, owner), now)
            .filter { since == null || it.date >= since }
            .forEach { AttendanceUploadWorker.enqueue(context, it) }
        AttendanceCheckWorker.schedule(context, Attendance.nextCheck(own, places, now), fromWorker)
    }

    /** After leaving [left]: asks whether the activity held there ended early, if one is in progress. */
    suspend fun leftPlace(context: Context, owner: String, left: String, inside: Set<String>) {
        val data = WidgetStore.data(context)?.takeIf { it.owner == owner } ?: return
        if (!AccountSettingsRepository(context).settings(owner).first().attendance) return
        val zone = data.profiles.find { it.id == owner }?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        LeftEarly.occurrence(data.schedules.filter { it.userId == owner }, AutoPlaces.places(context, owner), left, inside,
            ZonedDateTime.now(zone))?.let { EndEarlyPrompt.show(context, owner, it) }
    }
}

/** Looks again when the window of the next tracked activity opens, in case the phone is already there. */
class AttendanceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { AttendanceTracker.check(applicationContext, fromWorker = true) }
        return Result.success()
    }

    companion object {
        private const val NAME = "AttendanceCheck"

        /** From the worker itself the next run is appended, so replacing does not cancel the run in progress. */
        fun schedule(context: Context, at: Instant?, fromWorker: Boolean = false) {
            if (at == null) { if (!fromWorker) cancel(context); return }
            val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(1_000)
            WorkManager.getInstance(context).enqueueUniqueWork(NAME,
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AttendanceCheckWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
    }
}

/** Sends one automatic mark. The server keeps a mark set by hand, so sending it twice is harmless. */
class AttendanceUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(ID) ?: return Result.failure()
        val date = inputData.getString(DATE) ?: return Result.failure()
        val owner = inputData.getString(OWNER)
        return try {
            val auth = SupabaseClientProvider.client.auth
            auth.awaitInitialization()
            // Signed out or into another account since: the mark belonged to someone else.
            if (auth.currentUserOrNull()?.id != owner) return Result.success()
            AccountRepository().recordAttendance(id, date, ActivityAttendance.PRESENT, ActivityAttendance.AUTO)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: io.github.jan.supabase.exceptions.RestException) { Result.failure() }
        catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }

    companion object {
        private const val ID = "schedule_id"
        private const val DATE = "date"
        private const val OWNER = "owner"

        suspend fun enqueue(context: Context, key: Attendance.Key) {
            val owner = WidgetStore.data(context)?.owner ?: return
            WorkManager.getInstance(context).enqueueUniqueWork("attendance:${key.scheduleId}:${key.date}", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AttendanceUploadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(ID to key.scheduleId, DATE to key.date, OWNER to owner)).build())
        }
    }
}
