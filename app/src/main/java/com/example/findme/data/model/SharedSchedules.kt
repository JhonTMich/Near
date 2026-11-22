package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedSchedules(
    @SerialName("viewer_zone") val viewerZone: String,
    @SerialName("contact_zone") val contactZone: String,
    val viewer: List<ScheduleDto>,
    val contact: List<ScheduleDto>,
    /** Absent before the pauses migration is applied. */
    @SerialName("viewer_pauses") val viewerPauses: List<SchedulePause> = emptyList(),
    @SerialName("contact_pauses") val contactPauses: List<SchedulePause> = emptyList()
)
