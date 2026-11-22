package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.model.SchedulePause
import java.time.LocalDate

/**
 * Pauses are applied once, where the schedules are loaded, as cancelled occurrences marked [ScheduleException.paused].
 * Every view that reads occurrences (today, people, widget, reminders, free time) then respects them.
 * A date that already has its own change keeps it: a moved occurrence was set for that date on purpose.
 */
fun List<ScheduleDto>.withPauses(pauses: List<SchedulePause>): List<ScheduleDto> {
    val byOwner = pauses.groupBy { it.userId }
    return map { schedule ->
        val clean = schedule.withoutPauses()
        // A one-off activity was planned for its date, pause or not.
        if (clean.isOneOff) return@map clean
        val ranges = byOwner[schedule.userId].orEmpty().mapNotNull { it.range() }
        if (ranges.isEmpty()) return@map clean
        val added = ranges.flatMap { (start, end) ->
            generateSequence(start.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(schedule.dayOfWeek)))) { it.plusWeeks(1) }
                .takeWhile { !it.isAfter(end) }.toList()
        }.map { it.toString() }.filterNot { clean.occurrenceExceptions.containsKey(it) }
        if (added.isEmpty()) clean
        else clean.copy(occurrenceExceptions = clean.occurrenceExceptions + added.associateWith { ScheduleException(cancelled = true, paused = true) })
    }
}

fun ScheduleDto.withoutPauses(): ScheduleDto =
    if (occurrenceExceptions.values.none { it.paused }) this
    else copy(occurrenceExceptions = occurrenceExceptions.filterValues { !it.paused })

/** The pause of [owner] that covers [date], if any. */
fun List<SchedulePause>.covering(owner: String?, date: LocalDate): SchedulePause? = firstOrNull { pause ->
    pause.userId == owner && pause.range()?.let { (start, end) -> !date.isBefore(start) && !date.isAfter(end) } == true
}

fun SchedulePause.range(): Pair<LocalDate, LocalDate>? {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
    val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return null
    return (start to end).takeIf { !end.isBefore(start) }
}

/** Validation shared with the editor; the database checks the same limits. */
fun validatePause(title: String, start: LocalDate?, end: LocalDate?, today: LocalDate): String? = when {
    title.isBlank() || title.trim().length > 60 -> "El nombre debe tener entre 1 y 60 caracteres."
    start == null || end == null -> "Elige las fechas de inicio y fin."
    end.isBefore(start) -> "La fecha de fin debe ser igual o posterior a la de inicio."
    end.isBefore(today) -> "Esa pausa ya terminó."
    start.isAfter(today.plusDays(366)) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366 ->
        "Una pausa puede empezar dentro del próximo año y durar como máximo un año."
    else -> null
}
