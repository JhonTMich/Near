package com.example.findme.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.findme.R
import com.example.findme.data.local.AccountSettings
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.util.NotificationHelper
import com.example.findme.widget.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The reminder as a real alarm: a system alarm sound on the alarm stream, looping with vibration and a
 * full-screen screen over the lock screen until the person stops or snoozes it. Everything the ring needs
 * travels in the Intent, so it also works when it fires with Near closed.
 */
object ReminderAlarm {
    /** One ring. [startsAt] is the activity's start in epoch millis, or 0 for a test ring that cannot be snoozed. */
    data class Ringing(
        val id: String,
        val title: String,
        val body: String,
        val startsAt: Long,
        val sound: String?,
        val vibrate: Boolean,
        val snoozeMinutes: Int
    ) {
        val snoozeUntil: Instant? get() = snoozeUntil(Instant.now(), snoozeMinutes, startsAt)

        fun into(intent: Intent): Intent = intent.putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body).putExtra(EXTRA_STARTS_AT, startsAt).putExtra(EXTRA_SOUND, sound)
            .putExtra(EXTRA_VIBRATE, vibrate).putExtra(EXTRA_SNOOZE, snoozeMinutes)

        companion object {
            fun from(intent: Intent?): Ringing? {
                val id = intent?.getStringExtra(EXTRA_ID) ?: return null
                return Ringing(id, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_BODY).orEmpty(),
                    intent.getLongExtra(EXTRA_STARTS_AT, 0L), intent.getStringExtra(EXTRA_SOUND),
                    intent.getBooleanExtra(EXTRA_VIBRATE, true), intent.getIntExtra(EXTRA_SNOOZE, 5))
            }

            /** The ring for [id], with the sound, vibration and snooze the person chose. */
            fun of(id: String, title: String, body: String, startsAt: Long, settings: AccountSettings) =
                Ringing(id, title, body, startsAt, settings.alarmSound, settings.alarmVibrate, settings.alarmSnoozeMinutes)
        }
    }

    /**
     * When a snoozed alarm rings again: [minutes] from [now], but never after the activity starts, since
     * a reminder after the start is no longer a reminder. Null when it cannot be snoozed any more.
     */
    fun snoozeUntil(now: Instant, minutes: Int, startsAt: Long): Instant? {
        if (startsAt <= 0L) return null
        val start = Instant.ofEpochMilli(startsAt)
        if (!start.isAfter(now.plusSeconds(30))) return null
        val later = now.plusSeconds(minutes * 60L)
        return if (later.isBefore(start)) later else start
    }

    /** How long it rings before it stops by itself and leaves a normal reminder instead. */
    const val TIMEOUT_MILLIS = 2 * 60 * 1000L

    internal const val ACTION_RING = "com.example.findme.reminders.RING"
    internal const val ACTION_STOP = "com.example.findme.reminders.STOP"
    internal const val ACTION_SNOOZE = "com.example.findme.reminders.SNOOZE"
    private const val EXTRA_ID = "alarm_id"
    private const val EXTRA_TITLE = "alarm_title"
    private const val EXTRA_BODY = "alarm_body"
    private const val EXTRA_STARTS_AT = "alarm_starts_at"
    private const val EXTRA_SOUND = "alarm_sound"
    private const val EXTRA_VIBRATE = "alarm_vibrate"
    private const val EXTRA_SNOOZE = "alarm_snooze"
    internal const val NOTIFICATION_ID = 7103
    private const val SNOOZE_REQUEST = 7102

    private val current = MutableStateFlow<Ringing?>(null)
    /** What is ringing now, so the full-screen screen closes when the alarm stops from the notification. */
    val ringing: StateFlow<Ringing?> = current.asStateFlow()
    internal fun setRinging(value: Ringing?) { current.value = value }

    /**
     * Starts ringing. Android only lets an app start this from the background right after an exact alarm,
     * so it returns false when it was not allowed and the caller should fall back to a notification.
     */
    fun ring(context: Context, ringing: Ringing): Boolean = try {
        ContextCompat.startForegroundService(context, ringing.into(Intent(context, ReminderAlarmService::class.java).setAction(ACTION_RING)))
        true
    } catch (_: IllegalStateException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun stop(context: Context) {
        if (current.value == null) return
        runCatching { context.startService(Intent(context, ReminderAlarmService::class.java).setAction(ACTION_STOP)) }
    }

    fun snooze(context: Context) {
        if (current.value == null) return
        runCatching { context.startService(Intent(context, ReminderAlarmService::class.java).setAction(ACTION_SNOOZE)) }
    }

    private fun snoozeIntent(context: Context, ringing: Ringing? = null): PendingIntent {
        val intent = Intent(context, ReminderSnoozeReceiver::class.java)
        ringing?.into(intent)
        return PendingIntent.getBroadcast(context, SNOOZE_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun scheduleSnooze(context: Context, ringing: Ringing, at: Instant) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        ActivityReminders.scheduleAlarm(context, alarms, at.toEpochMilli(), snoozeIntent(context, ringing), clock = true)
    }

    /** Forgets a pending snooze and silences anything ringing, e.g. after signing out or turning reminders off. */
    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(snoozeIntent(context))
        stop(context)
    }

    /** The chosen sound first, then Android's alarm, ringtone and notification sounds, so it never rings silent. */
    fun sounds(chosen: String?): List<Uri> = listOfNotNull(
        chosen?.let { runCatching { Uri.parse(it) }.getOrNull() },
        Settings.System.DEFAULT_ALARM_ALERT_URI,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    ).distinct()

    /** The name Android shows for [uri], or "Predeterminado" for Android's default alarm. */
    fun soundTitle(context: Context, uri: String?): String {
        if (uri == null) return "Predeterminado del sistema"
        return runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Sonido elegido"
    }
}

/** Plays the alarm and keeps its notification while it rings. */
class ReminderAlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish(missed = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ReminderAlarm.ACTION_STOP -> finish(missed = false)
            ReminderAlarm.ACTION_SNOOZE -> {
                ReminderAlarm.ringing.value?.let { ringing ->
                    ringing.snoozeUntil?.let { ReminderAlarm.scheduleSnooze(this, ringing, it) }
                }
                finish(missed = false)
            }
            else -> {
                val ringing = ReminderAlarm.Ringing.from(intent)
                if (ringing == null) { finish(missed = false); return START_NOT_STICKY }
                start(ringing)
            }
        }
        return START_NOT_STICKY
    }

    private fun start(ringing: ReminderAlarm.Ringing) {
        val notification = notification(ringing)
        silence()
        // Published before the notification, whose full-screen intent may open the screen right away.
        ReminderAlarm.setRinging(ringing)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ReminderAlarm.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ReminderAlarm.NOTIFICATION_ID, notification)
        } catch (_: RuntimeException) {
            // Android refused to keep it in the foreground: leave the plain reminder instead of failing silently.
            ReminderAlarm.setRinging(null)
            NotificationHelper.show(this, NotificationHelper.Channel.Reminders, "reminder:${ringing.id}".hashCode(), ringing.title, ringing.body)
            stopSelf()
            return
        }
        play(ringing.sound)
        if (ringing.vibrate) vibrate()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ReminderAlarm.TIMEOUT_MILLIS)
        // Over the lock screen the full-screen intent opens it; with the phone in use, open it directly if allowed.
        runCatching { startActivity(ReminderAlarmActivity.intent(this)) }
    }

    private fun notification(ringing: ReminderAlarm.Ringing): Notification {
        NotificationHelper.createNotificationChannel(this)
        val screen = PendingIntent.getActivity(this, ReminderAlarm.NOTIFICATION_ID, ReminderAlarmActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(action: String, request: Int) = PendingIntent.getService(this, request,
            Intent(this, ReminderAlarmService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NotificationHelper.Channel.Alarms.id)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(ringing.title).setContentText(ringing.body)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setAutoCancel(false)
            .setContentIntent(screen).setFullScreenIntent(screen, true)
            .apply { if (ringing.snoozeUntil != null) addAction(R.drawable.ic_notification, "Posponer ${ringing.snoozeMinutes} min", action(ReminderAlarm.ACTION_SNOOZE, 7104)) }
            .addAction(R.drawable.ic_notification, "Detener", action(ReminderAlarm.ACTION_STOP, 7105))
            .build()
    }

    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private fun play(chosen: String?) {
        val audio = getSystemService(AUDIO_SERVICE) as? AudioManager
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
            .also { request -> audio?.requestAudioFocus(request) }
        for (uri in ReminderAlarm.sounds(chosen)) {
            val candidate = MediaPlayer()
            try {
                candidate.setAudioAttributes(attributes)
                candidate.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
                candidate.setDataSource(this, uri)
                candidate.isLooping = true
                candidate.prepare()
                candidate.start()
                player = candidate
                return
            } catch (_: Exception) {
                // A deleted or unreadable sound: try the next one.
                candidate.release()
            }
        }
    }

    private fun vibrate() {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as? Vibrator)
        if (device?.hasVibrator() != true) return
        vibrator = device
        @Suppress("DEPRECATION")
        device.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attributes)
    }

    private fun silence() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { (getSystemService(AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(it) }
        focus = null
    }

    /** Stops ringing; when nobody answered it, leaves the reminder as a normal notification. */
    private fun finish(missed: Boolean) {
        handler.removeCallbacks(timeout)
        val ringing = ReminderAlarm.ringing.value
        silence()
        ReminderAlarm.setRinging(null)
        if (missed && ringing != null) NotificationHelper.show(this, NotificationHelper.Channel.Reminders,
            "reminder:${ringing.id}".hashCode(), ringing.title, ringing.body)
        stopForeground(STOP_FOREGROUND_REMOVE)
        (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(ReminderAlarm.NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        silence()
        ReminderAlarm.setRinging(null)
        super.onDestroy()
    }
}

/** A snoozed alarm rings again, if reminders are still on, with the sound and vibration chosen now. */
class ReminderSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ringing = ReminderAlarm.Ringing.from(intent) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                val owner = WidgetStore.data(app)?.owner ?: return@launch
                val settings = AccountSettingsRepository(app).settings(owner).first()
                if (!settings.reminders || !settings.reminderAlarm) return@launch
                val fresh = ReminderAlarm.Ringing.of(ringing.id, ringing.title, ringing.body, ringing.startsAt, settings)
                if (!ReminderAlarm.ring(app, fresh)) NotificationHelper.show(app, NotificationHelper.Channel.Reminders,
                    "reminder:${ringing.id}".hashCode(), ringing.title, ringing.body)
            } finally { pending.finish() }
        }
    }
}
