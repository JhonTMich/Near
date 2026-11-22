package com.example.findme

import com.example.findme.data.model.*
import com.example.findme.domain.model.AccountExport
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AccountExportTest {
    private val me = AccountProfile("me", "Jhon", "America/Lima", "jhon")
    private val friend = AccountProfile("friend", "Ana", "Europe/Madrid", "ana")

    @Test fun exportsOwnDataAndNamesInsteadOfInternalIds() {
        val text = AccountExport.build(
            profile = me,
            schedules = listOf(
                ScheduleDto("2", "me", 2, "09:00:00", "10:00:00", "Clase", "Aula 4"),
                ScheduleDto("1", "me", 1, "08:00:00", "09:00:00", "Turno", "", "work"),
                ScheduleDto("3", "friend", 1, "08:00:00", "09:00:00", "Ajeno", "")
            ),
            statuses = listOf(StatusOverrideDto("me", "me", true, "En la biblioteca", null, "2026-11-16T12:00:00Z"),
                StatusOverrideDto("friend", "friend", true, "Privado", null, "2026-11-16T12:00:00Z")),
            quickMessages = listOf(QuickMessage("q", "me", "Llegué", "Ya estoy aquí")),
            connections = listOf(PersonConnection("c", "friend", "me")),
            people = listOf(me, friend),
            received = listOf(ConnectionSignal("s", "friend", "me", "Hola", "2026-11-16T10:00:00Z")),
            exportedAt = "2026-11-16T11:00:00Z"
        )
        val json = Json.parseToJsonElement(text).jsonObject
        assertEquals("jhon", json["profile"]!!.jsonObject["username"]!!.jsonPrimitive.content)
        val schedule = json["schedule"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        assertEquals(listOf("Turno", "Clase"), schedule)
        assertEquals(1, json["shared_status"]!!.jsonArray.size)
        assertEquals("Ana", json["connections"]!!.jsonArray.single().jsonObject["person"]!!.jsonPrimitive.content)
        assertEquals("Ana", json["received_messages"]!!.jsonArray.single().jsonObject["from"]!!.jsonPrimitive.content)
        for (internal in listOf("\"friend\"", "\"me\"", "Ajeno", "Privado")) assertFalse("Leaked $internal", text.contains(internal))
    }

    @Test fun exportsPausesOneOffActivitiesAndGroupsByName() {
        val text = AccountExport.build(me,
            schedules = listOf(ScheduleDto("1", "me", 4, "10:00", "11:00", "Médico", "", oneOffDate = "2026-12-03")),
            statuses = emptyList(), quickMessages = emptyList(), connections = listOf(PersonConnection("c", "friend", "me")),
            people = listOf(me, friend), received = emptyList(), exportedAt = "2026-11-22T11:00:00Z",
            pauses = listOf(SchedulePause("p", "me", "Vacaciones", "2026-12-20", "2027-01-04"),
                SchedulePause("q", "friend", "Ajena", "2026-12-20", "2027-01-04")),
            groups = listOf(PersonGroup("g", "me", "Familia")), groupMembers = listOf(PersonGroupMember("g", "friend")))
        val json = Json.parseToJsonElement(text).jsonObject
        assertEquals("2026-12-03", json["schedule"]!!.jsonArray.single().jsonObject["only_on"]!!.jsonPrimitive.content)
        assertEquals("Vacaciones", json["schedule_pauses"]!!.jsonArray.single().jsonObject["title"]!!.jsonPrimitive.content)
        val group = json["groups"]!!.jsonArray.single().jsonObject
        assertEquals("Familia", group["name"]!!.jsonPrimitive.content)
        assertEquals("Ana", group["people"]!!.jsonArray.single().jsonPrimitive.content)
        assertFalse(text.contains("Ajena"))
    }
}
