package com.example.findme

import com.example.findme.data.local.AccountSettings
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.places.AutoPlaces.Agenda
import com.example.findme.places.AutoPlaces.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StreetStatusTest {
    private val lima = ZoneId.of("America/Lima")
    private val monday = LocalDate.parse("2026-11-16")
    private val now = monday.atTime(12, 0).atZone(lima)
    private val instant = now.toInstant()
    private fun at(hour: Int, minute: Int = 0) = monday.atTime(hour, minute).atZone(lima).toInstant()
    private fun activity(id: String, start: String, end: String, day: Int = 1) =
        ScheduleDto(id, userId = "me", dayOfWeek = day, startTime = start, endTime = end, title = id, location = "")
    private val free = Agenda(busyUntil = null, nextStart = at(15), nextEnd = at(17))

    private fun street(agenda: Agenda = free, enabled: Boolean = true, hasPlaces: Boolean = true,
        inside: Set<String> = emptySet(), marker: String? = null, manual: Boolean = false) =
        AutoPlaces.street(enabled, hasPlaces, inside, marker, manual, agenda, instant)

    @Test fun outsideEveryPlaceSharesStreetUntilTheNextActivity() {
        assertEquals(Change.Street(180), street())
        // With nothing ahead it lasts as long as any status can.
        assertEquals(Change.Street(AutoPlaces.STATUS_MINUTES), street(Agenda(null, null, null)))
        assertEquals(Change.Street(AutoPlaces.STATUS_MINUTES), street(Agenda(null, at(12).plusSeconds(3 * 86_400), null)))
    }

    @Test fun theScheduleWinsDuringAnActivity() {
        val busy = Agenda(busyUntil = at(13), nextStart = at(15), nextEnd = at(17))
        assertEquals(Change.Nothing, street(busy))
        // «En la calle» shared before the activity is removed so people see the activity.
        assertEquals(Change.Clear, street(busy, marker = AutoPlaces.STREET_MARKER))
        // An activity about to start: nothing worth sharing.
        assertEquals(Change.Nothing, street(Agenda(null, instant.plusSeconds(30), null)))
    }

    @Test fun manualStatusesAndPlacesAreLeftAlone() {
        assertEquals(Change.Nothing, street(manual = true))
        assertEquals(Change.Nothing, street(inside = setOf("home")))
        assertEquals(Change.Nothing, street(inside = setOf("home"), marker = AutoPlaces.STREET_MARKER))
    }

    @Test fun offOrWithoutPlacesOnlyRemovesItsOwnStatus() {
        assertEquals(Change.Nothing, street(enabled = false))
        assertEquals(Change.Clear, street(enabled = false, marker = AutoPlaces.STREET_MARKER))
        assertEquals(Change.Nothing, street(enabled = false, marker = "home"))
        assertEquals(Change.Clear, street(hasPlaces = false, marker = AutoPlaces.STREET_MARKER))
    }

    @Test fun agendaFindsTheActivityInProgressAndTheNextOne() {
        val agenda = AutoPlaces.agenda(listOf(activity("class", "11:00", "13:00"), activity("lab", "15:00", "17:00"),
            activity("long", "10:00", "14:00")), now)
        assertEquals(at(14), agenda.busyUntil)
        assertEquals(at(15), agenda.nextStart)
        assertEquals(at(17), agenda.nextEnd)
        assertEquals(at(14), agenda.nextCheck)
    }

    @Test fun agendaRespectsCancellationsAndOvernightActivities() {
        val cancelled = activity("class", "11:00", "13:00")
            .copy(occurrenceExceptions = mapOf(monday.toString() to ScheduleException(cancelled = true)))
        val agenda = AutoPlaces.agenda(listOf(cancelled), now)
        assertNull(agenda.busyUntil)
        assertEquals(monday.plusWeeks(1).atTime(11, 0).atZone(lima).toInstant(), agenda.nextStart)
        // A Sunday night shift still running on Monday morning keeps the person busy.
        val night = activity("night", "22:00", "06:00", day = 7).copy(endsNextDay = true)
        val early = AutoPlaces.agenda(listOf(night), monday.atTime(5, 0).atZone(lima))
        assertEquals(at(6), early.busyUntil)
        assertEquals(at(6), Agenda(at(6), null, null).nextCheck)
    }

    @Test fun streetIsOffByDefaultAndPlacesKeepTheirZone() {
        assertFalse(AccountSettings().streetStatus)
        val campus = AutoPlace("uni", "En la universidad", -12.05, -77.08, 500f, "Av. Universitaria 1801, Lima")
        assertEquals(listOf(campus), AutoPlaces.decode(AutoPlaces.encode(listOf(campus))))
        // Places saved before addresses existed still load, with the old 150 m zone.
        val old = AutoPlaces.decode("""[{"id":"home","label":"En casa","latitude":-12.0,"longitude":-77.0}]""").single()
        assertNull(old.address)
        assertEquals(AutoPlaces.RADIUS, old.radius)
    }

    @Test fun enteringAPlaceReplacesStreet() {
        val home = AutoPlace("home", "En casa", -12.05, -77.04)
        assertEquals(Change.Share("En casa", "home"),
            AutoPlaces.desired(listOf(home), listOf("home"), marker = AutoPlaces.STREET_MARKER, manualStatus = false,
                entered = true, street = Change.Nothing))
    }
}
