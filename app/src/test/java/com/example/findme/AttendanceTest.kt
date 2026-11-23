package com.example.findme

import com.example.findme.attendance.Attendance
import com.example.findme.attendance.Attendance.Mark
import com.example.findme.attendance.LeftEarly
import com.example.findme.data.model.ActivityAttendance
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.domain.model.AccountExport
import com.example.findme.domain.model.EndEarly
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AttendanceTest {
    private val lima = ZoneId.of("America/Lima")
    private val monday = LocalDate.parse("2026-11-16")
    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(lima)
    private fun activity(id: String, start: String, end: String, category: String = "university", modality: String = "presencial") =
        ScheduleDto(id, userId = "me", dayOfWeek = 1, startTime = start, endTime = end, title = id, location = "",
            category = category, modality = modality)
    private val campus = AutoPlace("uni", "En la universidad", -12.05, -77.08, 500f, category = "university")
    private val home = AutoPlace("home", "En casa", -12.0, -77.0, category = AutoPlaces.NO_CATEGORY)
    private val places = listOf(campus, home)
    private val clase = activity("clase", "09:00", "11:00")

    @Test fun placesHoldActivitiesByCategory() {
        assertEquals(listOf(campus), AutoPlaces.placesFor(clase, places))
        assertTrue(AutoPlaces.placesFor(clase.copy(modality = "virtual"), places).isEmpty())
        assertTrue(AutoPlaces.placesFor(activity("cine", "18:00", "20:00", category = "free"), places).isEmpty())
        // Places saved before categories follow their label; an explicit "none" is respected.
        assertEquals("university", AutoPlace("old", "En la universidad", 0.0, 0.0).activityCategory)
        assertEquals("work", AutoPlace("old", "En el trabajo", 0.0, 0.0).activityCategory)
        assertNull(AutoPlace("old", "En la universidad", 0.0, 0.0, category = AutoPlaces.NO_CATEGORY).activityCategory)
        assertEquals("university", AutoPlaces.suggestedCategory("En la universidad"))
        assertNull(AutoPlaces.suggestedCategory("En casa"))
    }

    @Test fun beingAtTheRightPlaceDuringTheWindowCountsAsAttended() {
        val key = Attendance.Key("clase", monday.toString())
        assertEquals(listOf(key), Attendance.dueNow(listOf(clase), places, setOf("uni"), at(monday, 8, 45)))
        assertEquals(listOf(key), Attendance.dueNow(listOf(clase), places, setOf("uni"), at(monday, 10, 59)))
        assertTrue(Attendance.dueNow(listOf(clase), places, setOf("uni"), at(monday, 8, 44)).isEmpty())
        assertTrue(Attendance.dueNow(listOf(clase), places, setOf("uni"), at(monday, 11, 0)).isEmpty())
        assertTrue(Attendance.dueNow(listOf(clase), places, setOf("home"), at(monday, 9, 30)).isEmpty())
        assertTrue(Attendance.dueNow(listOf(clase.copy(modality = "virtual")), places, setOf("uni"), at(monday, 9, 30)).isEmpty())
    }

    @Test fun theNextCheckOpensFifteenMinutesBeforeTheNextTrackedActivity() {
        assertEquals(at(monday, 8, 45).toInstant(), Attendance.nextCheck(listOf(clase), places, at(monday, 7, 0)))
        assertEquals(at(monday.plusWeeks(1), 8, 45).toInstant(), Attendance.nextCheck(listOf(clase), places, at(monday, 8, 50)))
        assertNull(Attendance.nextCheck(listOf(clase), listOf(home), at(monday, 7, 0)))
    }

    @Test fun summariesCountMissedDatesOnlyWhenTrackedAndOnlySinceTurningItOn() {
        val cancelled = monday.minusWeeks(1).toString()
        val schedule = clase.copy(occurrenceExceptions = mapOf(cancelled to ScheduleException(cancelled = true)))
        val virtual = activity("online", "14:00", "15:00", modality = "virtual")
        val since = monday.minusWeeks(3)
        val records = listOf(ActivityAttendance("clase", monday.minusWeeks(2).toString(), ActivityAttendance.PRESENT),
            ActivityAttendance("online", monday.minusWeeks(2).toString(), ActivityAttendance.PRESENT, ActivityAttendance.MANUAL))
        val now = at(monday, 10, 0)
        val (tracked, manual) = Attendance.summaries(listOf(schedule, virtual, activity("cine", "18:00", "20:00", category = "free")),
            places, records, since, now)
        // Three past Mondays (one cancelled) and today's class still in progress is left out.
        assertEquals(listOf(monday.minusWeeks(2), monday.minusWeeks(3)), tracked.days.map { it.occurrence.originalDate })
        assertEquals(listOf(Mark.Attended, Mark.Missed), tracked.days.map { it.mark })
        assertEquals(50, tracked.rate)
        assertEquals(listOf(Mark.Unmarked, Mark.Attended, Mark.Unmarked), manual.days.map { it.mark })
        assertTrue(manual.days[1].manual)
        assertEquals(100, manual.rate)
    }

    @Test fun endingNowShortensOnlyToday() {
        val occurrence = listOf(clase).occurrencesOn(monday, lima).single()
        assertEquals(ScheduleException(date = monday.toString(), startTime = "09:00:00", endTime = "10:20:00"),
            EndEarly.change(occurrence, at(monday, 10, 20).plusSeconds(35)))
        assertEquals(ScheduleException(cancelled = true), EndEarly.change(occurrence, at(monday, 9, 0).plusSeconds(20)))
        assertNull(EndEarly.change(occurrence, at(monday, 8, 59)))
        assertNull(EndEarly.change(occurrence, at(monday, 11, 0)))
        // A night shift that began yesterday cannot be ended after midnight.
        val night = activity("night", "22:00", "06:00", category = "work").copy(dayOfWeek = 7, endsNextDay = true)
        val shift = listOf(night).occurrencesOn(monday, lima).single()
        assertNull(EndEarly.change(shift, at(monday, 2, 0)))
    }

    @Test fun leavingThePlaceEarlyAsksOnlyWithTimeLeftAndNoOtherPlaceOfIt() {
        val second = AutoPlace("uni2", "En la universidad", -12.1, -77.1, category = "university")
        assertEquals("clase", LeftEarly.occurrence(listOf(clase), places, "uni", emptySet(), at(monday, 9, 30))?.schedule?.id)
        assertNull(LeftEarly.occurrence(listOf(clase), places, "uni", emptySet(), at(monday, 10, 50)))
        assertNull(LeftEarly.occurrence(listOf(clase), places, "home", emptySet(), at(monday, 9, 30)))
        assertNull(LeftEarly.occurrence(listOf(clase), places + second, "uni", setOf("uni2"), at(monday, 9, 30)))
        assertNull(LeftEarly.occurrence(listOf(clase), places, "uni", emptySet(), at(monday, 8, 50)))
    }

    @Test fun theExportIncludesAttendance() {
        val profile = AccountProfile(id = "me", displayName = "Ana", timezone = "America/Lima")
        val export = AccountExport.build(profile, listOf(clase), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            "2026-11-23T00:00:00Z", attendance = listOf(ActivityAttendance("clase", monday.toString(), ActivityAttendance.ABSENT,
                ActivityAttendance.MANUAL)))
        assertTrue(export.contains("\"attendance\""))
        assertTrue(export.contains("\"activity\": \"clase\""))
        assertTrue(export.contains("\"status\": \"absent\""))
    }
}
