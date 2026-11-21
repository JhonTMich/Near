package com.example.findme

import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.places.AutoPlaces.Change
import org.junit.Assert.*
import org.junit.Test

class AutoPlacesTest {
    private val home = AutoPlace("home", "En casa", -12.05, -77.04)

    @Test fun arrivingSharesThePlaceUnlessAStatusWasSetByHand() {
        assertEquals(Change.Share("En casa", "home"), AutoPlaces.decide(true, home, marker = null, manualStatus = false))
        assertEquals(Change.Nothing, AutoPlaces.decide(true, home, marker = null, manualStatus = true))
        // Arriving at another saved place replaces the earlier automatic status.
        assertEquals(Change.Share("En casa", "home"), AutoPlaces.decide(true, home, marker = "work", manualStatus = false))
    }

    @Test fun leavingOnlyRemovesTheStatusThatPlaceSet() {
        assertEquals(Change.Clear, AutoPlaces.decide(false, home, marker = "home", manualStatus = false))
        assertEquals(Change.Nothing, AutoPlaces.decide(false, home, marker = "work", manualStatus = false))
        assertEquals(Change.Nothing, AutoPlaces.decide(false, home, marker = null, manualStatus = true))
    }

    @Test fun placesRoundTripAndBadDataIsIgnored() {
        val places = listOf(home, AutoPlace("uni", "En la universidad", -12.07, -77.08, 200f))
        assertEquals(places, AutoPlaces.decode(AutoPlaces.encode(places)))
        assertTrue(AutoPlaces.decode("no es json").isEmpty())
    }

    @Test fun labelsAreLikeStatuses() {
        assertNull(AutoPlaces.validateLabel("En casa"))
        assertNotNull(AutoPlaces.validateLabel("  "))
        assertNotNull(AutoPlaces.validateLabel("x".repeat(61)))
    }
}
