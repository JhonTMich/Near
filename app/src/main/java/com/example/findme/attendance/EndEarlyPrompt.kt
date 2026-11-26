package com.example.findme.attendance

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.findme.R
import com.example.findme.util.NotificationStyle.near
import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.data.repository.AccountRepository
import com.example.findme.domain.model.EndEarly
import com.example.findme.domain.model.ScheduleOccurrence
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.util.NotificationHelper
import com.example.findme.widget.GlanceWidgetWorker
import com.example.findme.widget.WidgetStore
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.time.ZonedDateTime

/** «¿Terminó Prácticas?» after leaving its place early, with a button that ends it now for this date. */
object EndEarlyPrompt {
    internal const val ACTION = "com.example.findme.END_EARLY"
    internal const val EXTRA_ID = "end_early_id"
    internal const val EXTRA_DATE = "end_early_date"
    internal const val EXTRA_OWNER = "end_early_owner"

    fun id(scheduleId: String, date: String) = "end_early:$scheduleId:$date".hashCode()

    fun show(context: Context, owner: String, occurrence: ScheduleOccurrence) {
        val scheduleId = occurrence.schedule.id
        val date = occurrence.originalDate.toString()
        val notification = id(scheduleId, date)
        val intent = Intent(context, EndEarlyReceiver::class.java).setAction(ACTION)
            .putExtra(EXTRA_ID, scheduleId).putExtra(EXTRA_DATE, date).putExtra(EXTRA_OWNER, owner)
        val end = PendingIntent.getBroadcast(context, notification, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationHelper.createNotificationChannel(context)
        val title = "¿Terminó ${occurrence.schedule.title.trim()}?"
        val until = occurrence.window.end.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT))
        val body = "Saliste de su lugar y en tu horario sigue hasta las $until."
        post(context, notification, NotificationCompat.Builder(context, NotificationHelper.Channel.Reminders.id)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true)
            .setContentIntent(NotificationHelper.openSchedule(context)).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_notification, "Terminar ahora", end)
            .near(context, NotificationHelper.Channel.Reminders, stack = false))
    }

    internal fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(id, builder.build()) }
        catch (_: SecurityException) { }
    }

    /** The occurrence still in progress in the cached schedule; null if it already ended or changed. */
    internal suspend fun current(context: Context, owner: String, scheduleId: String, date: String): Pair<ScheduleOccurrence, ZonedDateTime>? {
        val data = WidgetStore.data(context)?.takeIf { it.owner == owner } ?: return null
        val zone = data.profiles.find { it.id == owner }?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val occurrence = data.schedules.filter { it.userId == owner }.occurrencesOn(now.toLocalDate(), zone)
            .firstOrNull { it.schedule.id == scheduleId && it.originalDate.toString() == date } ?: return null
        return occurrence to now
    }
}

/** Ends the activity from the notification. Not exported: only the PendingIntent above reaches it. */
class EndEarlyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EndEarlyPrompt.ACTION) return
        val scheduleId = intent.getStringExtra(EndEarlyPrompt.EXTRA_ID) ?: return
        val date = intent.getStringExtra(EndEarlyPrompt.EXTRA_DATE) ?: return
        val owner = intent.getStringExtra(EndEarlyPrompt.EXTRA_OWNER) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val ended = try {
                withTimeoutOrNull(9_000) {
                    val auth = SupabaseClientProvider.client.auth
                    auth.awaitInitialization()
                    if (auth.currentUserOrNull()?.id != owner) return@withTimeoutOrNull false
                    val (occurrence, now) = EndEarlyPrompt.current(app, owner, scheduleId, date) ?: return@withTimeoutOrNull false
                    val change = EndEarly.change(occurrence, now) ?: return@withTimeoutOrNull false
                    val repository = AccountRepository()
                    if (occurrence.schedule.sourceId != null) repository.changeFollowedOccurrence(scheduleId, date, change)
                    else repository.changeScheduleOccurrence(scheduleId, date, change)
                    true
                } ?: false
            } catch (_: Exception) { false }
            try {
                if (ended) GlanceWidgetWorker.enqueueRefresh(app)
                NotificationHelper.createNotificationChannel(app)
                EndEarlyPrompt.post(app, EndEarlyPrompt.id(scheduleId, date),
                    NotificationCompat.Builder(app, NotificationHelper.Channel.Reminders.id)
                        .setSmallIcon(R.drawable.ic_notification).setAutoCancel(true)
                        .setContentIntent(NotificationHelper.openSchedule(app))
                        .setContentTitle(if (ended) "Actividad terminada por hoy" else "No se pudo terminar la actividad")
                        .setContentText(if (ended) "Tu gente te verá libre. Puedes restaurarla en Horario." else "Abre Near para terminarla desde Hoy.")
                        .near(app, NotificationHelper.Channel.Reminders, stack = false))
            } finally { pending.finish() }
        }
    }
}
