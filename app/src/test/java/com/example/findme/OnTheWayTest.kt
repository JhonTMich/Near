package com.example.findme

import com.example.findme.data.model.CurrentPlaceDto
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.widget.WidgetScene
import com.example.findme.widget.widgetStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** An in-person activity in progress shows «Yendo a …» while the phone has not reached its place. */
class OnTheWayTest {
    private val monday = ZonedDateTime.parse("2026-10-26T15:15:00-05:00[America/Lima]")
    private val iot = ScheduleDto(id = "iot", dayOfWeek = 1, startTime = "15:00:00", endTime = "17:00:00",
        title = "IoT", location = "Aula 204", category = "university")
    private fun place(label: String, awayFrom: List<String>?) =
        CurrentPlaceDto("me", label, "2026-10-27T15:00:00Z", awayFrom).asStatus()

    private fun status(schedule: ScheduleDto, place: StatusOverrideDto) =
        StatusRepository.calculateStatus("Persona", listOf(schedule), listOf(place), 1, monday) as UserCurrentStatus.InClass

    @Test fun onTheStreetDuringAnInPersonClassIsOnTheWay() {
        val state = status(iot, place(AutoPlaces.STREET, listOf("university")))
        assertTrue(state.onTheWay)
        assertEquals("Yendo a IoT", state.headline)
        assertEquals(AutoPlaces.STREET, state.place)
    }

    @Test fun atHomeDuringAnInPersonClassIsOnTheWayToo() {
        assertTrue(status(iot, place("En casa", listOf("university", "work"))).onTheWay)
    }

    @Test fun atTheCampusIsInClass() {
        val state = status(iot, place("En la universidad", listOf("work")))
        assertFalse(state.onTheWay)
        assertEquals("IoT", state.headline)
    }

    @Test fun aVirtualClassCanBeAttendedFromAnywhere() {
        assertFalse(status(iot.copy(modality = "virtual"), place(AutoPlaces.STREET, listOf("university"))).onTheWay)
    }

    @Test fun withoutAPlaceForTheCategoryNothingChanges() {
        // No university place saved: the class may be anywhere, so it is not on the way.
        assertFalse(status(iot, place(AutoPlaces.STREET, listOf("work"))).onTheWay)
        // Apps that do not send the categories yet.
        assertFalse(status(iot, place(AutoPlaces.STREET, null)).onTheWay)
    }

    @Test fun theWidgetShowsItOnTheStreet() {
        val widget = widgetStatus("Persona", listOf(iot), listOf(place(AutoPlaces.STREET, listOf("university"))), monday)
        assertEquals("Yendo a IoT", widget.title)
        assertTrue(widget.detail, widget.detail.startsWith("En la calle · Empezó a las "))
        assertTrue(widget.detail.endsWith(" · Aula 204"))
        assertEquals(WidgetScene.STREET, widget.scene)
        assertEquals("En camino", widget.label)
    }

    @Test fun awayFromListsTheCategoriesWithAPlaceThePhoneIsNotIn() {
        val campus = AutoPlace("uni", "En la universidad", 0.0, 0.0, category = "university")
        val office = AutoPlace("job", "En el trabajo", 0.0, 0.0, category = "work")
        val home = AutoPlace("home", "En casa", 0.0, 0.0, category = AutoPlaces.NO_CATEGORY)
        assertEquals(listOf("university", "work"), AutoPlaces.awayFrom(listOf(campus, office, home), setOf("home")))
        assertEquals(listOf("work"), AutoPlaces.awayFrom(listOf(campus, office, home), setOf("uni")))
        assertEquals(emptyList<String>(), AutoPlaces.awayFrom(listOf(home), emptySet()))
    }

    @Test fun onlyPlacesSendTheirCategories() {
        val json = Json { encodeDefaults = true }
        val status = StatusOverrideDto(isActive = true, customLocation = "Estudiando", expiresAt = "2026-10-26T18:00:00Z")
        assertFalse("away_from" in json.encodeToJsonElement(StatusOverrideDto.serializer(), status).jsonObject)
        assertFalse("away_from" in json.encodeToJsonElement(CurrentPlaceDto.serializer(),
            CurrentPlaceDto("me", "En casa", "2026-10-27T15:00:00Z")).jsonObject)
        val cached = place("En casa", listOf("university"))
        assertEquals(cached, json.decodeFromString(StatusOverrideDto.serializer(), json.encodeToString(StatusOverrideDto.serializer(), cached)))
    }
}
