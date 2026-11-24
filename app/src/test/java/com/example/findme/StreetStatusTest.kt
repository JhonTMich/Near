package com.example.findme

import com.example.findme.data.local.AccountSettings
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.places.AutoPlaces.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StreetStatusTest {
    private fun street(enabled: Boolean = true, hasPlaces: Boolean = true, inside: Set<String> = emptySet(),
        marker: String? = null) = AutoPlaces.street(enabled, hasPlaces, inside, marker)

    @Test fun outsideEveryPlaceSharesStreet() {
        assertEquals(Change.Street(AutoPlaces.PLACE_MINUTES), street())
        // It goes next to the activity, so nothing about the schedule holds it back.
        assertEquals(Change.Street(AutoPlaces.PLACE_MINUTES), street(marker = "home"))
    }

    @Test fun insideAPlaceStreetIsNotDecided() {
        assertEquals(Change.Nothing, street(inside = setOf("home")))
        assertEquals(Change.Nothing, street(inside = setOf("home"), marker = AutoPlaces.STREET_MARKER))
    }

    @Test fun offOrWithoutPlacesOnlyRemovesItsOwnPlace() {
        assertEquals(Change.Nothing, street(enabled = false))
        assertEquals(Change.Clear, street(enabled = false, marker = AutoPlaces.STREET_MARKER))
        assertEquals(Change.Nothing, street(enabled = false, marker = "home"))
        assertEquals(Change.Clear, street(hasPlaces = false, marker = AutoPlaces.STREET_MARKER))
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
            AutoPlaces.desired(listOf(home), listOf("home"), marker = AutoPlaces.STREET_MARKER, street = Change.Nothing))
    }
}
