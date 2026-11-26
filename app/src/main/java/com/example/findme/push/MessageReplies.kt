package com.example.findme.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.findme.R
import com.example.findme.util.NotificationStyle.near
import com.example.findme.data.local.AccountCache
import com.example.findme.data.network.SupabaseClientProvider
import com.example.findme.data.repository.AccountRepository
import com.example.findme.util.NotificationHelper
import com.example.findme.widget.WidgetStore
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Answers a received message from its notification, with free text or one of the account's quick messages. */
object MessageReplies {
    const val KEY_TEXT = "near_reply_text"
    const val EXTRA_RECIPIENT = "near_reply_recipient"
    const val EXTRA_NOTIFICATION = "near_reply_notification"
    const val EXTRA_OWNER = "near_reply_owner"
    const val ACTION = "com.example.findme.REPLY"
    const val MAX_LENGTH = 240

    /** The text to send, or null when it is empty or too long for the server. */
    fun clean(text: CharSequence?): String? = text?.toString()?.trim()?.takeIf { it.length in 1..MAX_LENGTH }

    /** Quick messages of [owner] cached on the device, offered as suggested replies. */
    suspend fun suggestions(context: Context, owner: String): List<String> = runCatching {
        AccountCache.load(context, owner)?.messages.orEmpty().map { it.message }.filter { clean(it) != null }.distinct().take(5)
    }.getOrDefault(emptyList())
}

class MessageReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MessageReplies.ACTION) return
        val recipient = intent.getStringExtra(MessageReplies.EXTRA_RECIPIENT) ?: return
        val owner = intent.getStringExtra(MessageReplies.EXTRA_OWNER) ?: return
        val notification = intent.getIntExtra(MessageReplies.EXTRA_NOTIFICATION, 0)
        val text = MessageReplies.clean(RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageReplies.KEY_TEXT))
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val sent = try {
                withTimeoutOrNull(9_000) {
                    if (text == null) return@withTimeoutOrNull false
                    val auth = SupabaseClientProvider.client.auth
                    auth.awaitInitialization()
                    // Only from the account that received the message, and only to someone it is still connected with.
                    if (auth.currentUserOrNull()?.id != owner) return@withTimeoutOrNull false
                    val connected = WidgetStore.data(app)?.takeIf { it.owner == owner }?.profiles.orEmpty().any { it.id == recipient }
                    if (!connected) return@withTimeoutOrNull false
                    AccountRepository().sendSignal(recipient, text)
                    true
                } ?: false
            } catch (_: Exception) { false }
            try { confirm(app, notification, text, sent) } finally { pending.finish() }
        }
    }

    /** Replaces the notification so the inline reply stops spinning and says what happened. */
    private fun confirm(context: Context, id: Int, text: String?, sent: Boolean) {
        NotificationHelper.createNotificationChannel(context)
        val body = if (sent) "Tú: $text" else "No se pudo enviar la respuesta. Abre Near para intentarlo de nuevo."
        val notification = NotificationCompat.Builder(context, NotificationHelper.Channel.Messages.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (sent) "Respuesta enviada" else "Respuesta sin enviar")
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOnlyAlertOnce(true).setAutoCancel(true).setSilent(true)
            .apply { if (sent) setTimeoutAfter(10_000) }
            .near(context, NotificationHelper.Channel.Messages, stack = false)
            .build()
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.notify(id, notification) }
        catch (_: SecurityException) { }
    }
}
