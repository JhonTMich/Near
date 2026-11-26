package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.ui.screen.account.followedChangeNotice
import com.example.findme.ui.screen.account.followedChanges
import com.example.findme.ui.screen.account.followedGroups
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowActivitiesTest {
    private fun activity(id: String, day: Int, start: String, date: String? = null) =
        ScheduleDto(id, dayOfWeek = day, startTime = start, endTime = "23:00", title = id, location = "", oneOffDate = date)

    @Test fun applyingSendsOnlyWhatChanged() {
        assertEquals(setOf("c") to setOf("a"), followedChanges(setOf("a", "b"), setOf("b", "c")))
        assertEquals(emptySet<String>() to emptySet<String>(), followedChanges(setOf("a"), setOf("a")))
    }

    @Test fun activitiesAreGroupedByDayThenDate() {
        val groups = followedGroups(listOf(activity("late", 1, "18:00"), activity("wed", 3, "09:00"),
            activity("early", 1, "08:00"), activity("once", 2, "10:00", "2026-12-01")))
        assertEquals(listOf("Lunes", "Miércoles", "Martes 1 de diciembre"), groups.map { it.first })
        assertEquals(listOf("early", "late"), groups.first().second.map { it.id })
    }

    @Test fun theNoticeSaysWhatChanged() {
        assertEquals("Añadiste 2 actividades a tu horario.", followedChangeNotice(2, 0))
        assertEquals("Quitaste 1 actividad de tu horario.", followedChangeNotice(0, 1))
        assertEquals("Horario actualizado: 1 actividad añadida y 2 actividades quitadas.", followedChangeNotice(1, 2))
    }
}
