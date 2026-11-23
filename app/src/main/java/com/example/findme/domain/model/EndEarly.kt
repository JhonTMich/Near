package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * «Terminar ahora»: the activity in progress ends at the current minute, only for this date. It is the
 * same per-date change as rescheduling by hand, so people see you free and it can be undone with
 * «Restaurar horario habitual». Ending it right after it started cancels it for that date instead.
 * An overnight activity that began yesterday cannot be ended after midnight: the server only accepts
 * per-date changes from today on.
 */
object EndEarly {
    private val time = DateTimeFormatter.ofPattern("HH:mm:00", Locale.ROOT)

    /** Null when [occurrence] is not in progress at [now]. */
    fun change(occurrence: ScheduleOccurrence, now: ZonedDateTime): ScheduleException? {
        val start = occurrence.window.start
        val end = occurrence.window.end.withZoneSameInstant(start.zone)
        val at = now.withZoneSameInstant(start.zone).withSecond(0).withNano(0)
        if (now.isBefore(start) || !now.isBefore(end) || at.toLocalDate() != start.toLocalDate()) return null
        if (!at.isAfter(start)) return ScheduleException(cancelled = true)
        return ScheduleException(date = start.toLocalDate().toString(), startTime = start.format(time), endTime = at.format(time))
    }
}
