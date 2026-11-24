package com.example.findme

import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.places.AutoPlaces.Change
import org.junit.Assert.*
import org.junit.Test

class AutoPlacesTest {
    private val home = AutoPlace("home", "En casa", -12.05, -77.04)

    private val uni = AutoPlace("uni", "En la universidad", -12.07, -77.08, 500f)
    private val places = listOf(home, uni)
    private val street = Change.Street(60)

    private fun desired(inside: List<String>, marker: String?, manual: Boolean = false, entered: Boolean = true,
        street: Change = Change.Nothing) = AutoPlaces.desired(places, inside, marker, manual, entered, street)

    @Test fun arrivingSharesThePlaceUnlessAStatusWasSetByHand() {
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = null))
        assertEquals(Change.Nothing, desired(listOf("home"), marker = null, manual = true))
        // Arriving home from «En la calle» or from another place replaces that automatic status.
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = AutoPlaces.STREET_MARKER))
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = "uni"))
    }

    @Test fun aStatusAlreadySharedIsNotSentAgain() {
        assertEquals(Change.Nothing, desired(listOf("home"), marker = "home"))
        assertEquals(Change.Nothing, desired(listOf("uni", "home"), marker = "uni"))
    }

    @Test fun leavingUpdatesTheStatusEvenIfTheEarlierAttemptFailed() {
        // Left the university: «En la calle» when it is on, otherwise the place's status is removed.
        assertEquals(street, desired(emptyList(), marker = "uni", entered = false, street = street))
        assertEquals(Change.Clear, desired(emptyList(), marker = "uni", entered = false))
        // A retry after the status was already removed changes nothing.
        assertEquals(Change.Nothing, desired(emptyList(), marker = null, entered = false))
        assertEquals(Change.Nothing, desired(emptyList(), marker = null, manual = true, entered = false))
    }

    @Test fun leavingOnePlaceWhileInAnotherSharesTheOther() {
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = "uni", entered = false))
        // A status removed by hand stays removed.
        assertEquals(Change.Nothing, desired(listOf("home"), marker = null, entered = false))
        // Inside both, the latest place entered wins.
        assertEquals(Change.Share("En la universidad", "uni"), desired(listOf("home", "uni"), marker = null))
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
