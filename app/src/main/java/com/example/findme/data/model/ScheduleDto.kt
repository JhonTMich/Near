package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ScheduleDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("day_of_week") val dayOfWeek: Int, // 1 = Lunes ... 7 = Domingo
    @SerialName("start_time") val startTime: String, // "HH:MM:SS"
    @SerialName("end_time") val endTime: String,   // "HH:MM:SS"
    val title: String,
    val location: String,
    val category: String = "university",
    val modality: String = "presencial",
    @SerialName("ends_next_day") val endsNextDay: Boolean = false,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("occurrence_exceptions") val occurrenceExceptions: Map<String, ScheduleException> = emptyMap(),
    /** "yyyy-MM-dd" for an activity that happens only on that date; null for a weekly one. Omitted when null,
     *  so weekly saves keep working on a server without the column. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("one_off_date") val oneOffDate: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("source_id") val sourceId: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("source_owner_id") val sourceOwnerId: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    @SerialName("followed_customized") val followedCustomized: Boolean = false
) {
    val isOneOff: Boolean get() = oneOffDate != null
}
