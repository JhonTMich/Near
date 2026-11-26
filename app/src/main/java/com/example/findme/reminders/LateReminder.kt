package com.example.findme.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.ScheduleOccurrence
import com.example.findme.domain.model.nextOccurrence
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.util.NotificationHelper
import com.example.findme.widget.WidgetStore
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * «IoT empezó hace 5 min y aún no llegas»: only for you, a few minutes after an in-person activity starts
 * while the phone is in none of its saved places. Virtual activities, and those with no place of their
 * category, can be attended from anywhere, so they never warn. It follows the reminders switch and reads the
 * cached schedule and the places on the phone, so it needs no network.
 */
object LateReminder {
    const val AFTER_MINUTES = 5L
    /** A late or delayed run still warns within this time after it became due, never after the activity. */
    const val GRACE_MINUTES = 15L

    /** The activities that are due a warning now: started [AFTER_MINUTES] ago and the phone not at their place. */
    fun due(schedules: List<ScheduleDto>, places: List<AutoPlace>, inside: Set<String>, now: ZonedDateTime): List<ScheduleOccurrence> =
        schedules.occurrencesOn(now.toLocalDate(), now.zone).filter { occurrence ->
            val warnAt = occurrence.window.start.plusMinutes(AFTER_MINUTES)
            val own = AutoPlaces.placesFor(occurrence.schedule, places)
            own.isNotEmpty() && own.none { it.id in inside } &&
                !now.isBefore(warnAt) && now.isBefore(warnAt.plusMinutes(GRACE_MINUTES)) && now.isBefore(occurrence.window.end)
        }

    /** When the next warning may be due: [AFTER_MINUTES] after the next in-person activity with a place. */
    fun next(schedules: List<ScheduleDto>, places: List<AutoPlace>, now: ZonedDateTime): Instant? =
        schedules.filter { AutoPlaces.placesFor(it, places).isNotEmpty() }
            .mapNotNull { it.nextOccurrence(now.minusMinutes(AFTER_MINUTES))?.window?.start?.plusMinutes(AFTER_MINUTES)?.toInstant() }
            .filter { it.isAfter(now.toInstant()) }
            .minOrNull()

    fun text(occurrence: ScheduleOccurrence, places: List<AutoPlace>): Pair<String, String> {
        val title = "${occurrence.schedule.title.trim()} empezó hace $AFTER_MINUTES min"
        val where = AutoPlaces.placesFor(occurrence.schedule, places).joinToString(" o ") { "«${it.label}»" }
        return title to "Aún no llegas a $where."
    }

    fun id(occurrence: ScheduleOccurrence) = "late:${occurrence.schedule.id}:${occurrence.originalDate}".hashCode()

    /** Plans the next check from the cached schedule; with reminders off or no places it only cancels. */
    suspend fun schedule(context: Context, fromWorker: Boolean = false) {
        // From the worker nothing is cancelled: its own run is the one in progress.
        fun stop() { if (!fromWorker) cancel(context) }
        val data = WidgetStore.data(context) ?: return stop()
        if (!AccountSettingsRepository(context).settings(data.owner).first().reminders) return stop()
        val zone = zone(data.profiles.find { it.id == data.owner }?.timezone)
        val at = next(data.schedules.filter { it.userId == data.owner }, AutoPlaces.places(context, data.owner), ZonedDateTime.now(zone))
            ?: return stop()
        LateReminderWorker.enqueue(context, at, fromWorker)
    }

    /** Run by the worker: refreshes where the phone is when it can, warns, then plans the next check. */
    suspend fun fire(context: Context) {
        val data = WidgetStore.data(context) ?: return
        val owner = data.owner
        if (!AccountSettingsRepository(context).settings(owner).first().reminders) return
        val places = AutoPlaces.places(context, owner)
        val now = ZonedDateTime.now(zone(data.profiles.find { it.id == owner }?.timezone))
        val own = data.schedules.filter { it.userId == owner }
        if (due(own, places, AutoPlaces.inside(context, owner), now).isNotEmpty() && AutoPlaces.hasBackground(context)) {
            // A geofence entry may still be on its way: a fresh fix avoids warning someone who already arrived.
            // Whatever it finds is shared too, so «Yendo a …» ends as soon as the phone is there.
            if (runCatching { AutoPlaces.refreshInside(context, owner) }.getOrDefault(false))
                com.example.findme.places.PlaceStatusWorker.enqueue(context, owner)
        }
        due(own, places, AutoPlaces.inside(context, owner), now).forEach { occurrence ->
            val (title, body) = text(occurrence, places)
            NotificationHelper.show(context, NotificationHelper.Channel.Reminders, id(occurrence), title, body)
        }
    }

    fun cancel(context: Context) = LateReminderWorker.cancel(context)

    private fun zone(id: String?): ZoneId = id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}

class LateReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { LateReminder.fire(applicationContext) }
        runCatching { LateReminder.schedule(applicationContext, fromWorker = true) }
        return Result.success()
    }

    companion object {
        private const val NAME = "LateReminder"

        /** From the worker itself the next run is appended, so replacing does not cancel the run in progress. */
        fun enqueue(context: Context, at: Instant, fromWorker: Boolean = false) {
            val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(1_000)
            WorkManager.getInstance(context).enqueueUniqueWork(NAME,
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<LateReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }

        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
    }
}
