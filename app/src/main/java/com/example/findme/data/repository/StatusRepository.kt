package com.example.findme.data.repository

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.occurrencesOn
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object StatusRepository {
    val SYSTEM_ZONE: ZoneId get() = ZoneId.systemDefault()
    private val placeLabels = setOf("en casa", "en la universidad", "en el trabajo", "en el gimnasio", "en la calle")

    fun calculateStatus(
        displayName: String,
        schedules: List<ScheduleDto>,
        overrides: List<StatusOverrideDto>,
        selectedDayOfWeek: Int,
        nowInLima: ZonedDateTime = ZonedDateTime.now(SYSTEM_ZONE)
    ): UserCurrentStatus {
        val today = nowInLima.toLocalDate()
        val selectedDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(selectedDayOfWeek)))
        val currentOccurrences = schedules.occurrencesOn(today, nowInLima.zone)
        val daySchedules = (if (selectedDate == today) currentOccurrences
            else schedules.occurrencesOn(selectedDate, nowInLima.zone)).map { it.schedule }

        val current = overrides.filter { overrideDto ->
            overrideDto.isActive && parseIsoToInstant(overrideDto.expiresAt)?.isAfter(nowInLima.toInstant()) == true
        }
        val placeStatus = current.firstOrNull { it.isPlace }
        val place = placeStatus?.customLocation?.trim()?.takeIf { it.isNotEmpty() }
        // Older versions wrote automatic place labels into personal_status. A live place supersedes
        // those location-only labels, so "En casa" cannot hide today's work and contradict the fix.
        // Activity announcements such as "Estudiando" still take precedence over the schedule.
        val activeOverride = current.firstOrNull { status ->
            !status.isPlace && !(place != null && status.customMessage.isNullOrBlank() &&
                status.customLocation.trim().lowercase(java.util.Locale.ROOT) in placeLabels)
        }

        val active = currentOccurrences.firstOrNull { !nowInLima.isBefore(it.window.start) && nowInLima.isBefore(it.window.end) }

        // Account users can explicitly share a change of plans, even during a scheduled activity.
        if (activeOverride != null) {
            return UserCurrentStatus.Override(displayName, activeOverride.customLocation, activeOverride.customMessage,
                activeOverride.expiresAt, place = place, selectedDayOfWeek = selectedDayOfWeek, todaySchedules = daySchedules)
        }

        // An activity is active from its start time (inclusive) until its end (exclusive).
        if (active != null) {
            val currentSchedule = active.schedule
            val window = active.window
            val start = window.start
            val end = window.end
            val totalSec = Duration.between(start, end).seconds.coerceAtLeast(1)
            val elapsedSec = Duration.between(start, nowInLima).seconds.coerceAtLeast(0)
            val remainingMin = (Duration.between(nowInLima, end).seconds / 60).coerceAtLeast(0)
            val progress = (elapsedSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)

            return UserCurrentStatus.InClass(
                displayName = displayName,
                currentSchedule = currentSchedule,
                remainingMinutes = remainingMin,
                progress = progress,
                place = place,
                selectedDayOfWeek = selectedDayOfWeek,
                todaySchedules = daySchedules,
                onTheWay = onTheWay(currentSchedule, placeStatus?.takeIf { place != null })
            )
        }

        // No scheduled activity or active announcement.
        return UserCurrentStatus.FreeTime(
            place = place,
            displayName = displayName,
            selectedDayOfWeek = selectedDayOfWeek,
            todaySchedules = daySchedules
        )
    }

    /**
     * An in-person activity whose category the shared place says the person is away from: they have a saved
     * place for it and are not there yet. A virtual one can be attended from anywhere, so it never is.
     */
    internal fun onTheWay(schedule: ScheduleDto, place: StatusOverrideDto?): Boolean =
        schedule.modality != "virtual" && place?.awayFrom?.contains(schedule.category) == true

    fun parseLocalTimeOrNull(timeStr: String): LocalTime? {
        val normalized = timeStr.trim().let { value ->
            // Preserve support for a single-digit hour, while accepting PostgreSQL fractional seconds.
            if (value.indexOf(':') == 1) "0$value" else value
        }
        return try {
            LocalTime.parse(normalized)
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }

    fun parseIsoToInstant(isoString: String): Instant? {
        return try {
            Instant.parse(isoString)
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(isoString).toInstant()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(isoString).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
