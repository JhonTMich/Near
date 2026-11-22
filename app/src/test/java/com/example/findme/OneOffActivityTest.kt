package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.SchedulePause
import com.example.findme.domain.model.conflictMessage
import com.example.findme.domain.model.findScheduleConflict
import com.example.findme.domain.model.nextOccurrence
import com.example.findme.domain.model.occurrencesOn
import com.example.findme.domain.model.validateOneOffDate
import com.example.findme.domain.model.withPauses
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OneOffActivityTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.now(zone).plusDays(10)
    private val medico = ScheduleDto("medico", "own", date.dayOfWeek.value, "10:00", "11:00", "Médico", "", oneOffDate = date.toString())

    @Test fun occursOnlyOnItsDate() {
        assertEquals(1, listOf(medico).occurrencesOn(date, zone).size)
        assertTrue(listOf(medico).occurrencesOn(date.plusWeeks(1), zone).isEmpty())
        assertTrue(listOf(medico).occurrencesOn(date.minusWeeks(1), zone).isEmpty())
        assertEquals(date, medico.nextOccurrence(date.minusDays(3).atStartOfDay(zone))?.date)
        assertNull(medico.nextOccurrence(date.plusDays(1).atStartOfDay(zone)))
    }

    @Test fun aPauseDoesNotHideIt() {
        val pause = SchedulePause("p", "own", "Vacaciones", date.minusDays(1).toString(), date.plusDays(1).toString())
        assertEquals(1, listOf(medico).withPauses(listOf(pause)).occurrencesOn(date, zone).size)
    }

    @Test fun clashesAreCheckedOnTheDate() {
        val weekly = ScheduleDto("clase", "own", date.dayOfWeek.value, "10:30", "12:00", "Clase", "")
        assertEquals(weekly, findScheduleConflict(listOf(weekly), medico))
        assertEquals(medico, findScheduleConflict(listOf(medico), weekly))
        val otherWeek = medico.copy(id = "otra", oneOffDate = date.plusWeeks(1).toString())
        assertNull(findScheduleConflict(listOf(medico), otherWeek))
        val past = medico.copy(id = "pasada", oneOffDate = LocalDate.now(zone).minusWeeks(2).toString(),
            dayOfWeek = LocalDate.now(zone).minusWeeks(2).dayOfWeek.value)
        assertNull(findScheduleConflict(listOf(past), weekly.copy(dayOfWeek = past.dayOfWeek)))
        assertTrue(medico.conflictMessage().contains("Médico"))
    }

    @Test fun dateWindow() {
        val today = LocalDate.of(2026, 11, 22)
        assertNull(validateOneOffDate(today, today))
        assertNotNull(validateOneOffDate(today.minusDays(1), today))
        assertNotNull(validateOneOffDate(today.plusDays(367), today))
    }
}
