package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A plan proposed from the common free time. Instants, so each person reads it in their own zone. */
@Serializable
data class PlanProposal(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val title: String,
    val location: String = "",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    /** pending, accepted, declined or cancelled. */
    val status: String = "pending",
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("sender_schedule_id") val senderScheduleId: String? = null,
    @SerialName("recipient_schedule_id") val recipientScheduleId: String? = null
)
