package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Dates ("yyyy-MM-dd", inclusive) in the owner's zone during which the weekly schedule does not apply. */
@Serializable
data class SchedulePause(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String
)
