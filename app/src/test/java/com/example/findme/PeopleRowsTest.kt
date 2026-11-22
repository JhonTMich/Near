package com.example.findme

import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.widget.PeopleRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PeopleRowsTest {
    private val now = Instant.parse("2026-11-23T10:30:00Z") // Monday
    private val profiles = listOf(AccountProfile("own", "Yo", "UTC"), AccountProfile("ana", "Ana", "UTC"),
        AccountProfile("beto", "Beto", "UTC"), AccountProfile("caro", "Caro", "UTC"))

    @Test fun busyPeopleFirstAndNeverTheOwner() {
        val clase = ScheduleDto("c", "caro", 1, "10:00", "11:00", "Clase", "Aula 2")
        val status = StatusOverrideDto("beto", "beto", true, "En camino", expiresAt = now.plusSeconds(3600).toString())
        val rows = PeopleRows.build("own", profiles, listOf(clase), listOf(status), now)
        assertEquals(listOf("Beto", "Caro", "Ana"), rows.map { it.name })
        assertTrue(rows[0].busy && rows[1].busy)
        assertFalse(rows[2].busy)
        assertEquals("Clase", rows[1].title)
        assertEquals("Libre", rows[2].title)
    }

    @Test fun capacityFollowsTheHeight() {
        assertEquals(1, PeopleRows.capacity(110f, 1f))
        assertEquals(3, PeopleRows.capacity(190f, 1f))
        assertEquals(2, PeopleRows.capacity(190f, 1.3f))
        assertEquals(6, PeopleRows.capacity(900f, 1f))
    }
}
