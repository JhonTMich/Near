package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.model.SchedulePause
import com.example.findme.data.model.SharedSchedules
import com.example.findme.domain.model.commonFreeTime
import com.example.findme.domain.model.covering
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.domain.model.validatePause
import com.example.findme.domain.model.withPauses
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SchedulePausesTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 12, 21)
    private val clase = ScheduleDto("clase", "own", 1, "09:00", "10:00", "Clase", "")
    private val vacaciones = SchedulePause("p", "own", "Vacaciones", "2026-12-21", "2027-01-03")

    @Test fun pausedDatesHideTheWeeklyOccurrences() {
        val paused = listOf(clase).withPauses(listOf(vacaciones))
        assertTrue(paused.occurrencesOn(monday, zone).isEmpty())
        assertTrue(paused.occurrencesOn(monday.plusWeeks(1), zone).isEmpty())
        assertEquals(1, paused.occurrencesOn(monday.plusWeeks(2), zone).size)
    }

    @Test fun onlyTheOwnersPausesApply() {
        val other = clase.copy(id = "otra", userId = "other")
        val paused = listOf(clase, other).withPauses(listOf(vacaciones))
        assertEquals(listOf("otra"), paused.occurrencesOn(monday, zone).map { it.schedule.id })
    }

    @Test fun aMovedOccurrenceKeepsItsOwnChange() {
        val moved = clase.copy(occurrenceExceptions = mapOf("2026-12-21" to
            ScheduleException(date = "2026-12-22", startTime = "11:00", endTime = "12:00")))
        val paused = listOf(moved).withPauses(listOf(vacaciones))
        assertEquals(1, paused.occurrencesOn(monday.plusDays(1), zone).size)
        assertFalse(paused.single().occurrenceExceptions.getValue("2026-12-21").paused)
    }

    @Test fun applyingAgainReplacesTheEarlierPauses() {
        val once = listOf(clase).withPauses(listOf(vacaciones))
        assertEquals(once, once.withPauses(listOf(vacaciones)))
        val removed = once.withPauses(emptyList())
        assertEquals(clase, removed.single())
    }

    @Test fun pausedContactIsFreeInTheCommonTime() {
        val busyAllDay = ScheduleDto("dia", "other", 1, "00:00", "00:00", "Turno", "", endsNextDay = true)
        val pair = SharedSchedules("UTC", "UTC", emptyList(), listOf(busyAllDay),
            contactPauses = listOf(vacaciones.copy(userId = "other")))
        val days = commonFreeTime(pair, monday.atStartOfDay(zone).toInstant())
        assertTrue(days.first().slots.isNotEmpty())
    }

    @Test fun coveringAndValidation() {
        assertEquals(vacaciones, listOf(vacaciones).covering("own", LocalDate.of(2027, 1, 3)))
        assertNull(listOf(vacaciones).covering("own", LocalDate.of(2027, 1, 4)))
        val today = LocalDate.of(2026, 12, 1)
        assertNull(validatePause("Feriado", today, today, today))
        assertNotNull(validatePause("", today, today, today))
        assertNotNull(validatePause("Viaje", today.plusDays(2), today.plusDays(1), today))
        assertNotNull(validatePause("Viaje", today.minusDays(5), today.minusDays(1), today))
        assertNotNull(validatePause("Viaje", today, today.plusDays(400), today))
    }
}
