package com.example.findme

import com.example.findme.domain.model.IcsImport
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IcsImportTest {
    private val lima = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 22)
    private fun calendar(vararg events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" +
        events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    @Test fun weeklyClassesWithTheirDays() {
        val result = IcsImport.parse(calendar("""
            UID:clase-1
            SUMMARY:Cálculo II
            LOCATION:Aula 201\, Pabellón B
            DTSTART;TZID=America/Lima:20260901T080000
            DTEND;TZID=America/Lima:20260901T100000
            RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261220T000000Z
        """.trimIndent().replace("\n", "\r\n")), lima, today)
        val clase = result.candidates.single()
        assertEquals(listOf(2, 4), clase.days)
        assertEquals("08:00", clase.start)
        assertEquals("10:00", clase.end)
        assertEquals("Aula 201, Pabellón B", clase.location)
        assertEquals(2, clase.toSchedules("university").size)
        assertEquals(clase.toSchedules("university"), clase.toSchedules("university"))
    }

    @Test fun utcAndOtherZonesAreConvertedToTheAccountZone() {
        val result = IcsImport.parse(calendar("UID:r\r\nSUMMARY:Reunión\r\nDTSTART:20261125T150000Z\r\nDTEND:20261125T160000Z",
            "UID:m\r\nSUMMARY:Madrid\r\nDTSTART;TZID=Europe/Madrid:20261126T200000\r\nDURATION:PT30M"), lima, today)
        val (reunion, madrid) = result.candidates
        assertEquals(LocalDate.of(2026, 11, 25), reunion.date)
        assertEquals("10:00", reunion.start)
        assertEquals("14:00", madrid.start)
        assertEquals("14:30", madrid.end)
        assertFalse(reunion.weekly)
    }

    @Test fun overnightShiftsEndTheNextDay() {
        val shift = IcsImport.parse(calendar("UID:t\r\nSUMMARY:Turno\r\nDTSTART:20261123T220000\r\nDTEND:20261124T060000\r\nRRULE:FREQ=WEEKLY"),
            lima, today).candidates.single()
        assertTrue(shift.endsNextDay)
        assertEquals(listOf(1), shift.days)
    }

    @Test fun unsupportedEventsAreSkippedAndCounted() {
        val result = IcsImport.parse(calendar(
            "UID:a\r\nSUMMARY:Feriado\r\nDTSTART;VALUE=DATE:20261208",
            "UID:b\r\nSUMMARY:Pasado\r\nDTSTART:20261101T100000\r\nDTEND:20261101T110000",
            "UID:c\r\nSUMMARY:Mensual\r\nDTSTART:20261201T100000\r\nRRULE:FREQ=MONTHLY",
            "UID:d\r\nSUMMARY:Terminó\r\nDTSTART:20260301T100000\r\nRRULE:FREQ=WEEKLY;UNTIL=20260601T000000Z",
            "UID:e\r\nSUMMARY:Cada dos semanas\r\nDTSTART:20261201T100000\r\nRRULE:FREQ=WEEKLY;INTERVAL=2",
            "UID:f\r\nSUMMARY:Cancelado\r\nSTATUS:CANCELLED\r\nDTSTART:20261201T100000",
            "UID:g\r\nSUMMARY:Sigue\r\nDTSTART:20261201T100000\r\nDTEND:20261201T110000"), lima, today)
        assertEquals(listOf("Sigue"), result.candidates.map { it.title })
        assertEquals(6, result.skipped)
    }

    @Test fun foldedLinesAreJoined() {
        val result = IcsImport.parse(calendar("UID:x\r\nSUMMARY:Taller de\r\n  programación\r\nDTSTART:20261130T090000\r\nDTEND:20261130T100000"), lima, today)
        assertEquals("Taller de programación", result.candidates.single().title)
    }
}
