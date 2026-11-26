package com.example.findme.util

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.findme.R
import com.example.findme.util.NotificationHelper.Channel

/**
 * Near's own look in the notification shade, so its notifications stand out among many: the brand violet,
 * a picture on the right (the person's initial, or the mascot on the colour of the kind), the kind next to
 * the app name, and the same kind stacked under one summary.
 */
object NotificationStyle {
    const val BRAND = 0xFF5B3FD1.toInt()

    /** The colour of each kind, from the app's palette. */
    fun color(channel: Channel): Int = when (channel) {
        Channel.Reminders, Channel.Alarms -> 0xFF5B3FD1.toInt()
        Channel.Messages -> 0xFF6A4CC9.toInt()
        Channel.Activities -> 0xFF2E7D3D.toInt()
        Channel.Statuses -> 0xFF3D6FB6.toInt()
        Channel.Requested -> 0xFF9A5A16.toInt()
    }

    /** Shown next to «Near» in the header: what this is at a glance. */
    fun kind(channel: Channel): String = when (channel) {
        Channel.Reminders -> "Recordatorio"
        Channel.Alarms -> "Alarma"
        Channel.Messages -> "Mensaje"
        Channel.Activities -> "Actividad"
        Channel.Statuses -> "Estado"
        Channel.Requested -> "Aviso"
    }

    /** «3 mensajes», for the summary of a stack. */
    fun count(channel: Channel, n: Int): String {
        val (one, many) = when (channel) {
            Channel.Reminders -> "recordatorio" to "recordatorios"
            Channel.Alarms -> "alarma" to "alarmas"
            Channel.Messages -> "mensaje" to "mensajes"
            Channel.Activities -> "actividad" to "actividades"
            Channel.Statuses -> "cambio de estado" to "cambios de estado"
            Channel.Requested -> "aviso" to "avisos"
        }
        return "$n ${if (n == 1) one else many}"
    }

    /** About people: their initial. About you: the mascot. */
    private fun personal(channel: Channel) = channel in setOf(Channel.Messages, Channel.Activities, Channel.Statuses, Channel.Requested)

    fun group(channel: Channel) = "near.${channel.id}"
    private fun summaryId(channel: Channel) = "summary:${channel.id}".hashCode()

    /** «Ana Torres te propone…» → «A». */
    fun initial(title: String): String = title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "N"

    /** A round picture: [initial] in white, or the mascot, on [color]. */
    fun avatar(context: Context, color: Int, initial: String?): Bitmap {
        val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        if (initial != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = 0xFFFFFFFF.toInt(); textSize = size * .46f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(initial, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        } else ContextCompat.getDrawable(context, R.drawable.ic_notification)?.let { mascot ->
            val inset = (size * .2f).toInt()
            mascot.setBounds(inset, inset, size - inset, size - inset)
            mascot.setTint(0xFFFFFFFF.toInt())
            mascot.draw(canvas)
        }
        return bitmap
    }

    /** Applies the look; [stack] puts it in the stack of its kind (not for an ongoing alarm). */
    fun NotificationCompat.Builder.near(context: Context, channel: Channel, title: String? = null, stack: Boolean = true): NotificationCompat.Builder {
        setColor(color(channel))
        setSubText(kind(channel))
        setLargeIcon(avatar(context, color(channel), title?.takeIf { personal(channel) }?.let(::initial)))
        if (stack) setGroup(group(channel)).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        return this
    }

    /**
     * With two or more of a kind on screen, a summary groups them («Near · 3 mensajes») so they fold into one
     * entry; with fewer it is removed. Runs after each notification of the kind is posted.
     */
    /** One notification of a stack, as its summary lists it. */
    data class Line(val id: Int, val title: String, val text: String, val at: Long)

    fun summarize(context: Context, channel: Channel, target: android.app.PendingIntent? = null, posted: Line? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        // notify() is asynchronous: the one just posted may not be listed yet, so it is added by hand.
        val listed = manager.activeNotifications.filter {
            it.notification.group == group(channel) && it.id != summaryId(channel) && it.id != posted?.id
        }.map { child ->
            val extras = child.notification.extras
            Line(child.id, extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty(),
                extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty(), child.postTime)
        }
        val children = (listed + listOfNotNull(posted)).sortedByDescending { it.at }
        if (children.size < 2) { manager.cancel(summaryId(channel)); return }
        val inbox = NotificationCompat.InboxStyle().setSummaryText(count(channel, children.size))
        children.take(5).forEach { line ->
            inbox.addLine(listOf(line.title, line.text).filter { it.isNotBlank() }.joinToString(": "))
        }
        val summary = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification).setColor(color(channel))
            .setContentTitle(count(channel, children.size).replaceFirstChar { it.uppercase() })
            .setContentText(children.first().title)
            .setStyle(inbox).setGroup(group(channel)).setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN).setAutoCancel(true)
            .setSilent(true).apply { target?.let(::setContentIntent) }
            .build()
        try { manager.notify(summaryId(channel), summary) } catch (_: SecurityException) { }
    }
}
