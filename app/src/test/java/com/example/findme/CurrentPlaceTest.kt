package com.example.findme

import com.example.findme.data.model.CurrentPlaceDto
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.widget.widgetStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

/** The current place goes next to the activity or the status, never instead of it. */
class CurrentPlaceTest {
    private val monday = ZonedDateTime.parse("2026-10-26T10:30:00-05:00[America/Lima]")
    private val virtualClass = ScheduleDto(id = "english", dayOfWeek = 1, startTime = "10:00:00", endTime = "11:00:00",
        title = "Inglés", location = "", category = "university", modality = "virtual")
    private val home = CurrentPlaceDto("me", "En casa", "2026-10-27T15:00:00Z").asStatus()
    private val studying = StatusOverrideDto(isActive = true, customLocation = "Estudiando", expiresAt = "2026-10-26T18:00:00Z")

    private fun status(schedules: List<ScheduleDto>, statuses: List<StatusOverrideDto>, now: ZonedDateTime = monday) =
        StatusRepository.calculateStatus("Persona", schedules, statuses, 1, now)

    @Test fun aVirtualClassAtHomeShowsBoth() {
        val state = status(listOf(virtualClass), listOf(home))
        assertTrue(state is UserCurrentStatus.InClass)
        assertEquals("Inglés", (state as UserCurrentStatus.InClass).currentSchedule.title)
        assertEquals("En casa", state.place)
    }

    @Test fun freeAtHomeIsStillFree() {
        val state = status(emptyList(), listOf(home))
        assertTrue(state is UserCurrentStatus.FreeTime)
        assertEquals("En casa", state.place)
    }

    @Test fun aStatusSharedByHandKeepsThePlaceNextToIt() {
        val state = status(listOf(virtualClass), listOf(home, studying))
        assertTrue(state is UserCurrentStatus.Override)
        assertEquals("Estudiando", (state as UserCurrentStatus.Override).customLocation)
        assertEquals("En casa", state.place)
    }

    @Test fun anExpiredPlaceIsNotShown() {
        assertNull(status(emptyList(), listOf(home), monday.plusDays(2)).place)
        assertNull(status(emptyList(), emptyList()).place)
    }

    @Test fun theWidgetPutsThePlaceFirstInTheDetail() {
        assertEquals("En casa · Sin actividad", widgetStatus("Persona", emptyList(), listOf(home), monday).detail)
        assertTrue(widgetStatus("Persona", emptyList(), listOf(home), monday).free)
    }

    @Test fun onlyPlacesCarryTheirKind() {
        // personal_status has no kind column: a status must never send one.
        val json = Json { encodeDefaults = true }
        assertFalse("kind" in json.encodeToJsonElement(StatusOverrideDto.serializer(), studying).jsonObject)
        // The local cache keeps the kind of a place, and statuses saved before it existed read as statuses.
        assertEquals(home, json.decodeFromString(StatusOverrideDto.serializer(), json.encodeToString(StatusOverrideDto.serializer(), home)))
        assertFalse(json.decodeFromString(StatusOverrideDto.serializer(),
            """{"is_active":true,"custom_location":"En clase","expires_at":"2026-10-26T18:00:00Z"}""").isPlace)
    }
}
