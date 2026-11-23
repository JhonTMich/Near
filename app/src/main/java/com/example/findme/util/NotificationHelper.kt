package com.example.findme.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.findme.R

object NotificationHelper {
    const val OPEN_PEOPLE = "com.example.findme.OPEN_PEOPLE"
    const val OPEN_SCHEDULE = "com.example.findme.OPEN_SCHEDULE"

    /** One Android channel per kind, so each can also be silenced from the system settings. */
    enum class Channel(val id: String, val title: String, val description: String) {
        Activities("near_activities", "Actividades de tu gente", "Cuando una persona conectada comienza una actividad."),
        Statuses("findme_status_channel", "Estados de tu gente", "Cuando tu gente comparte, cambia o quita su estado."),
        Messages("findme_messages", "Mensajes de tu gente", "Mensajes rápidos que recibes."),
        Reminders("near_reminders", "Recordatorios de tus actividades", "Un aviso antes de que empiece cada actividad de tu horario."),
        Requested("near_requested", "Avisos que pediste", "Cuando alguien queda libre o responde a un plan que propusiste."),
        Alarms("near_alarms", "Alarmas de tus actividades", "La alarma que suena antes de una actividad cuando eliges el modo alarma.")
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        Channel.entries.forEach { channel ->
            manager.createNotificationChannel(NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = channel.description
                    // The alarm service plays the chosen sound and vibration itself, on the alarm stream.
                    if (channel == Channel.Alarms) { setSound(null, null); enableVibration(false) }
                })
        }
    }

    private fun open(context: Context, action: String): PendingIntent {
        val intent = Intent(context, com.example.findme.MainActivity::class.java).apply { this.action = action }
        return PendingIntent.getActivity(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Opens Near on your schedule, e.g. from the alarm icon in the status bar. */
    fun openSchedule(context: Context): PendingIntent = open(context, OPEN_SCHEDULE)

    /** Status and activity changes of one person replace each other instead of piling up. */
    fun personId(personId: String) = "person:$personId".hashCode()
    fun messageId(messageId: String) = "message:$messageId".hashCode()

    /** Who can be answered from the notification: the account that received it and the sender. */
    data class Reply(val owner: String, val recipient: String, val suggestions: List<String>)

    private fun replyAction(context: Context, id: Int, reply: Reply): NotificationCompat.Action {
        val input = androidx.core.app.RemoteInput.Builder(com.example.findme.push.MessageReplies.KEY_TEXT)
            .setLabel("Responder").setChoices(reply.suggestions.toTypedArray()).build()
        val intent = Intent(context, com.example.findme.push.MessageReplyReceiver::class.java)
            .setAction(com.example.findme.push.MessageReplies.ACTION)
            .putExtra(com.example.findme.push.MessageReplies.EXTRA_RECIPIENT, reply.recipient)
            .putExtra(com.example.findme.push.MessageReplies.EXTRA_OWNER, reply.owner)
            .putExtra(com.example.findme.push.MessageReplies.EXTRA_NOTIFICATION, id)
        // Mutable so Android can add the typed text; the explicit component keeps it from going anywhere else.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, id, intent, flags)
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Responder", pending)
            .addRemoteInput(input).setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build()
    }

    fun show(context: Context, channel: Channel, id: Int, title: String, text: String, reply: Reply? = null) {
        createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, if (channel == Channel.Reminders) OPEN_SCHEDULE else OPEN_PEOPLE))
            .setOnlyAlertOnce(true).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == Channel.Messages) NotificationCompat.CATEGORY_MESSAGE else if (channel == Channel.Reminders) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .apply { reply?.let { addAction(replyAction(context, id, it)) } }
            .build()
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(id, notification) }
        catch (_: SecurityException) { }
    }

    fun showMessageNotification(context: Context, id: String, sender: String, message: String, reply: Reply? = null) =
        show(context, Channel.Messages, messageId(id), sender, message, reply)

    fun showStatusNotification(context: Context, personId: String, title: String, message: String, activity: Boolean = false) =
        show(context, if (activity) Channel.Activities else Channel.Statuses, personId(personId), title, message)
}
