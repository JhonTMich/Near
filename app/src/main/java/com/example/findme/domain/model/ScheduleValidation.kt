package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Shared validation for the editor; database constraints remain authoritative. */
fun validateSchedule(day: Int, start: String, end: String, title: String, endsNextDay: Boolean = false): String? {
    if (day !in 1..7) return "Selecciona un día válido."
    if (title.isBlank() || title.trim().length > 120) return "El título debe tener entre 1 y 120 caracteres."
    val startTime = runCatching { scheduleTimeNanos(start) }.getOrNull()?.takeIf { it < 86_400_000_000_000L }
        ?: return "La hora de inicio debe usar el formato HH:mm."
    val endTime = runCatching { scheduleTimeNanos(end) }.getOrNull()
        ?: return "La hora de fin debe usar el formato HH:mm."
    if (!endsNextDay && endTime <= startTime) return "Activa «Termina al día siguiente» si el turno cruza la medianoche."
    if (endsNextDay && endTime > startTime) return "El turno puede durar como máximo 24 horas. Revisa la hora de fin."
    return null
}

fun findScheduleConflict(
    schedules: List<ScheduleDto>,
    activity: ScheduleDto
): ScheduleDto? {
    val virtualClass = activity.category == "university" && activity.modality == "virtual"
    return schedules.firstOrNull { other ->
        if (other.id == activity.id) return@firstOrNull false
        val otherVirtualClass = other.category == "university" && other.modality == "virtual"
        if ((virtualClass && other.category == "work") || (activity.category == "work" && otherVirtualClass)) {
            return@firstOrNull false
        }
        activity.overlaps(other)
    }
}

fun ScheduleDto.conflictMessage(): String {
    val day = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
    val overnight = if (endsNextDay) " del día siguiente" else ""
    return "Coincide con «$title» el $day (${formatScheduleTime(startTime)}–${formatScheduleTime(endTime)}$overnight). Cambia el horario o el día."
}

private const val DAY = 86_400_000_000_000L

/**
 * Moves the end together with a new start so the activity keeps its length.
 * Returns the new end ("HH:mm") and whether it now finishes the next day, or null if
 * the current range can't be measured (then the end is left untouched).
 */
fun shiftScheduleEnd(oldStart: String, oldEnd: String, endsNextDay: Boolean, newStart: String): Pair<String, Boolean>? {
    val from = runCatching { scheduleTimeNanos(oldStart) }.getOrNull() ?: return null
    val to = runCatching { scheduleTimeNanos(oldEnd) }.getOrNull() ?: return null
    val target = runCatching { scheduleTimeNanos(newStart) }.getOrNull()?.takeIf { it < DAY } ?: return null
    val duration = if (endsNextDay) to + DAY - from else to - from
    if (duration !in 1..DAY) return null
    val end = target + duration
    val time = LocalTime.ofNanoOfDay(end % DAY)
    return String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute) to (end >= DAY)
}
