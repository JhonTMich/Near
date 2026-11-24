package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto

sealed interface UserCurrentStatus {
    val displayName: String
    val selectedDayOfWeek: Int
    val todaySchedules: List<ScheduleDto>
    /** Where the person is now, from their saved places («En casa»): shown next to the status, not instead. */
    val place: String? get() = null

    data class Override(
        override val displayName: String,
        val customLocation: String,
        val customMessage: String?,
        val expiresAt: String,
        val concurrentVirtualClass: ScheduleDto? = null,
        override val place: String? = null,
        override val selectedDayOfWeek: Int,
        override val todaySchedules: List<ScheduleDto>
    ) : UserCurrentStatus

    data class InClass(
        override val displayName: String,
        val currentSchedule: ScheduleDto,
        val remainingMinutes: Long,
        val progress: Float, // 0.0f to 1.0f
        override val place: String? = null,
        override val selectedDayOfWeek: Int,
        override val todaySchedules: List<ScheduleDto>
    ) : UserCurrentStatus

    data class FreeTime(
        override val displayName: String,
        override val place: String? = null,
        override val selectedDayOfWeek: Int,
        override val todaySchedules: List<ScheduleDto>
    ) : UserCurrentStatus

    data class Loading(
        override val displayName: String = "Usuario",
        override val selectedDayOfWeek: Int = 1,
        override val todaySchedules: List<ScheduleDto> = emptyList()
    ) : UserCurrentStatus

    data class Error(
        val message: String,
        override val displayName: String = "Usuario",
        override val selectedDayOfWeek: Int = 1,
        override val todaySchedules: List<ScheduleDto> = emptyList()
    ) : UserCurrentStatus
}
