package com.example.findme.attendance

import com.example.findme.data.model.ActivityAttendance
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.ScheduleOccurrence
import com.example.findme.domain.model.nextOccurrence
import com.example.findme.domain.model.occurrences
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Attendance to your university and work activities, decided on the phone. An in-person activity with a
 * saved place of its category is tracked by itself: being in that place from 15 minutes before it starts
 * until it ends counts as attended, and a past date with no mark counts as missed. Other activities (virtual,
 * or with no place of their category) are only marked by hand; without a mark they stay unmarked.
 */
object Attendance {
    /** Arriving this long before the start already counts. */
    const val EARLY_MINUTES = 15L
    /** Categories whose attendance matters; free time is never tracked. */
    val CATEGORIES = AutoPlaces.CATEGORIES

    data class Key(val scheduleId: String, val date: String)

    enum class Mark { Attended, Missed, Unmarked }

    data class Day(val occurrence: ScheduleOccurrence, val mark: Mark, val manual: Boolean)

    data class Summary(val schedule: ScheduleDto, val automatic: Boolean, val days: List<Day>) {
        val attended get() = days.count { it.mark == Mark.Attended }
        val missed get() = days.count { it.mark == Mark.Missed }
        /** Share of marked dates that were attended, or null with nothing marked yet. */
        val rate: Int? get() = (attended + missed).takeIf { it > 0 }?.let { attended * 100 / it }
    }

    fun counted(schedule: ScheduleDto) = schedule.category in CATEGORIES

    /** Tracked by itself: in person and with at least one saved place of its category. */
    fun automatic(schedule: ScheduleDto, places: List<AutoPlace>) = AutoPlaces.placesFor(schedule, places).isNotEmpty()

    private fun inWindow(occurrence: ScheduleOccurrence, now: ZonedDateTime) =
        !now.isBefore(occurrence.window.start.minusMinutes(EARLY_MINUTES)) && now.isBefore(occurrence.window.end)

    /** The dates that count as attended now: in their window, with the phone in one of their places. */
    fun dueNow(schedules: List<ScheduleDto>, places: List<AutoPlace>, inside: Set<String>, now: ZonedDateTime): List<Key> =
        (schedules.occurrencesOn(now.toLocalDate(), now.zone) + schedules.occurrencesOn(now.toLocalDate().plusDays(1), now.zone))
            .distinctBy { it.schedule.id to it.originalDate }
            .filter { inWindow(it, now) && AutoPlaces.placesFor(it.schedule, places).any { place -> place.id in inside } }
            .map { Key(it.schedule.id, it.originalDate.toString()) }

    /** When to look again: the next window of an automatic activity opens (15 minutes before it starts). */
    fun nextCheck(schedules: List<ScheduleDto>, places: List<AutoPlace>, now: ZonedDateTime): Instant? =
        schedules.filter { automatic(it, places) }
            .mapNotNull { it.nextOccurrence(now.plusMinutes(EARLY_MINUTES))?.window?.start?.minusMinutes(EARLY_MINUTES)?.toInstant() }
            .minOrNull()

    /**
     * Every finished date since [since] of the counted activities, newest first, with its mark. A date still
     * in progress or ahead is left out, so it never shows as missed before it ends.
     */
    fun summaries(schedules: List<ScheduleDto>, places: List<AutoPlace>, records: List<ActivityAttendance>,
        since: LocalDate, now: ZonedDateTime): List<Summary> {
        val marks = records.associateBy { Key(it.scheduleId, it.occurrenceDate) }
        return schedules.filter(::counted).map { schedule ->
            val automatic = automatic(schedule, places)
            val days = schedule.occurrences(since, now.toLocalDate(), now.zone)
                .filter { !it.window.end.isAfter(now) && !it.originalDate.isBefore(since) }
                .sortedByDescending { it.window.start.toInstant() }
                .map { occurrence ->
                    val record = marks[Key(schedule.id, occurrence.originalDate.toString())]
                    val mark = when {
                        record?.present == true -> Mark.Attended
                        record != null -> Mark.Missed
                        automatic -> Mark.Missed
                        else -> Mark.Unmarked
                    }
                    Day(occurrence, mark, record?.source == ActivityAttendance.MANUAL)
                }.toList()
            Summary(schedule, automatic, days)
        }.filter { it.days.isNotEmpty() }
    }
}

/** Leaving the place of an activity well before it ends: Near asks whether it finished early. */
object LeftEarly {
    /** Less than this left and it is just leaving a bit early; nothing to ask. */
    const val MIN_REMAINING_MINUTES = 15L

    /**
     * The activity in progress that happens at [left], when the phone is in none of its places any more,
     * at least [MIN_REMAINING_MINUTES] remain and it can still be ended today.
     */
    fun occurrence(schedules: List<ScheduleDto>, places: List<AutoPlace>, left: String, inside: Set<String>,
        now: ZonedDateTime): ScheduleOccurrence? =
        schedules.occurrencesOn(now.toLocalDate(), now.zone).firstOrNull { occurrence ->
            val own = AutoPlaces.placesFor(occurrence.schedule, places)
            !now.isBefore(occurrence.window.start) &&
                !occurrence.window.end.isBefore(now.plusMinutes(MIN_REMAINING_MINUTES)) &&
                own.any { it.id == left } && own.none { it.id in inside } &&
                com.example.findme.domain.model.EndEarly.change(occurrence, now) != null
        }
}
