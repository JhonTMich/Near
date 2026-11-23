package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether you went to one date of an activity. Missed dates of tracked activities have no row. */
@Serializable
data class ActivityAttendance(
    @SerialName("schedule_id") val scheduleId: String,
    @SerialName("occurrence_date") val occurrenceDate: String,
    val status: String,
    val source: String = AUTO
) {
    val present: Boolean get() = status == PRESENT

    companion object {
        const val PRESENT = "present"
        const val ABSENT = "absent"
        const val AUTO = "auto"
        const val MANUAL = "manual"
    }
}
