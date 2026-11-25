package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.reminders.LateReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LateReminderTest {
    private val lima = ZoneId.of("America/Lima")
    private val monday = LocalDate.parse("2026-11-16")
    private fun at(hour: Int, minute: Int = 0) = monday.atTime(hour, minute).atZone(lima)
    private val iot = ScheduleDto("iot", userId = "me", dayOfWeek = 1, startTime = "15:00", endTime = "17:00",
        title = "IoT", location = "", category = "university")
    private val campus = AutoPlace("uni", "En la universidad", -12.05, -77.08, 500f, category = "university")
    private val home = AutoPlace("home", "En casa", -12.0, -77.0, category = AutoPlaces.NO_CATEGORY)
    private val places = listOf(campus, home)

    @Test fun warnsFiveMinutesAfterTheStartWhenNotThere() {
        assertTrue(LateReminder.due(listOf(iot), places, setOf("home"), at(15, 4)).isEmpty())
        assertEquals(listOf("iot"), LateReminder.due(listOf(iot), places, setOf("home"), at(15, 5)).map { it.schedule.id })
        assertEquals(listOf("iot"), LateReminder.due(listOf(iot), places, emptySet(), at(15, 12)).map { it.schedule.id })
        // A run delayed past the grace period does not warn about an activity long under way.
        assertTrue(LateReminder.due(listOf(iot), places, emptySet(), at(15, 21)).isEmpty())
    }

    @Test fun doesNotWarnWhenAlreadyThere() {
        assertTrue(LateReminder.due(listOf(iot), places, setOf("uni"), at(15, 5)).isEmpty())
    }

    @Test fun virtualOrPlacelessActivitiesNeverWarn() {
        assertTrue(LateReminder.due(listOf(iot.copy(modality = "virtual")), places, emptySet(), at(15, 5)).isEmpty())
        assertTrue(LateReminder.due(listOf(iot.copy(category = "work")), places, emptySet(), at(15, 5)).isEmpty())
        assertTrue(LateReminder.due(listOf(iot), listOf(home), emptySet(), at(15, 5)).isEmpty())
    }

    @Test fun nextCheckIsFiveMinutesAfterTheNextStart() {
        assertEquals(at(15, 5).toInstant(), LateReminder.next(listOf(iot), places, at(10)))
        // Once due, the next check is the following week.
        assertEquals(at(15, 5).plusWeeks(1).toInstant(), LateReminder.next(listOf(iot), places, at(15, 5)))
        assertNull(LateReminder.next(listOf(iot.copy(modality = "virtual")), places, at(10)))
    }

    @Test fun theTextSaysWhereYouHaveNotArrived() {
        val occurrence = LateReminder.due(listOf(iot), places, emptySet(), at(15, 5)).single()
        assertEquals("IoT empezó hace 5 min" to "Aún no llegas a «En la universidad».", LateReminder.text(occurrence, places))
    }
}
