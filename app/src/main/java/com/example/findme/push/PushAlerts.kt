package com.example.findme.push

import com.example.findme.data.local.AccountAlert
import com.example.findme.data.local.AccountSettings
import com.example.findme.domain.model.Plans
import com.example.findme.util.NotificationHelper

/** What one push (FCM data message sent by the send-push function) shows, before the switches are checked. */
/** [person] is the connected person the alert is about: the sender of a message, or whose status or activity changed. */
/** [requested] alerts were asked for explicitly, so the alert switches and an open app do not hide them. */
data class PushAlert(val alert: AccountAlert, val channel: NotificationHelper.Channel, val id: Int, val title: String, val text: String,
    val person: String, val requested: Boolean = false,
    /** Invitations have no local notification equivalent while the account screen is open. */
    val showWhileVisible: Boolean = false)

object PushAlerts {
    const val STATUS_ENDED = "Vuelve a mostrar su horario."
    const val NOW_FREE = "Ya está libre. Es un buen momento para escribirle."
    fun freeId(personId: String) = "free:$personId".hashCode()
    fun planId(planId: String) = "plan:$planId".hashCode()
    fun scheduleFollowId(requestId: String) = "schedule-follow:$requestId".hashCode()

    fun statusText(location: String, message: String?) =
        listOfNotNull(location.trim(), message?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

    fun activityText(title: String, location: String?) =
        "Actividad en curso: " + listOfNotNull(title.trim(), location?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

    /** Null for unknown or incomplete messages. */
    fun from(data: Map<String, String>): PushAlert? {
        val name = data["name"]?.takeIf { it.isNotBlank() } ?: "Tu gente"
        val person = data["user_id"]
        return when (data["type"]) {
            "message" -> {
                val id = data["id"] ?: return null
                val text = data["message"]?.takeIf { it.isNotBlank() } ?: return null
                // The database names the author of a message sender_id, not user_id.
                val sender = data["sender_id"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Messages, NotificationHelper.Channel.Messages, NotificationHelper.messageId(id), name, text, sender)
            }
            "status" -> {
                val location = data["location"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Statuses, NotificationHelper.Channel.Statuses, NotificationHelper.personId(person ?: return null),
                    name, statusText(location, data["message"]), person)
            }
            "status_end" -> PushAlert(AccountAlert.Statuses, NotificationHelper.Channel.Statuses,
                NotificationHelper.personId(person ?: return null), name, STATUS_ENDED, person)
            "activity" -> {
                val title = data["title"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Activities, NotificationHelper.Channel.Activities, NotificationHelper.personId(person ?: return null),
                    name, activityText(title, data["location"]), person)
            }
            "free" -> PushAlert(AccountAlert.Activities, NotificationHelper.Channel.Requested,
                freeId(person ?: return null), name, NOW_FREE, person, requested = true)
            "plan" -> {
                val id = data["id"] ?: return null
                val title = data["title"]?.takeIf { it.isNotBlank() } ?: return null
                val time = Plans.whenText(data["starts_at"] ?: return null, data["ends_at"], java.time.ZoneId.systemDefault())
                PushAlert(AccountAlert.Messages, NotificationHelper.Channel.Messages, planId(id), "$name te propone un plan",
                    "$title · $time", person ?: return null, showWhileVisible = true)
            }
            "plan_answer" -> {
                val id = data["id"] ?: return null
                val title = data["title"]?.takeIf { it.isNotBlank() } ?: return null
                val time = Plans.whenText(data["starts_at"] ?: return null, null, java.time.ZoneId.systemDefault())
                val accepted = data["accepted"] == "true"
                PushAlert(AccountAlert.Messages, NotificationHelper.Channel.Requested, planId(id),
                    if (accepted) "$name aceptó tu plan" else "$name no puede",
                    if (accepted) "$title · $time. Ya está en el horario de ambos." else "$title · $time", person ?: return null,
                    requested = true)
            }
            "schedule_follow" -> {
                val id = data["id"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Messages, NotificationHelper.Channel.Messages, scheduleFollowId(id),
                    "$name quiere sincronizar tu horario", "Revisa la solicitud en Personas para aceptarla o rechazarla.",
                    person?.takeIf { it.isNotBlank() } ?: return null, showWhileVisible = true)
            }
            "schedule_follow_answer" -> {
                val id = data["id"]?.takeIf { it.isNotBlank() } ?: return null
                PushAlert(AccountAlert.Messages, NotificationHelper.Channel.Requested, scheduleFollowId(id),
                    "$name aceptó la sincronización", "Ya puedes elegir qué actividades de su horario sincronizar.",
                    person?.takeIf { it.isNotBlank() } ?: return null, requested = true)
            }
            else -> null
        }
    }

    fun allowed(alert: PushAlert, settings: AccountSettings) = alert.requested || when (alert.alert) {
        AccountAlert.Activities -> settings.activities
        AccountAlert.Statuses -> settings.statuses
        AccountAlert.Messages -> settings.messages
    }

    fun shouldShow(alert: PushAlert, settings: AccountSettings, accountScreenVisible: Boolean) =
        allowed(alert, settings) && (!accountScreenVisible || alert.requested || alert.showWhileVisible)
}
