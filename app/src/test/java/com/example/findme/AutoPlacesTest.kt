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
    private val street = Change.Street(AutoPlaces.PLACE_MINUTES)

    private fun desired(inside: List<String>, marker: String?, street: Change = Change.Nothing, stale: Boolean = false) =
        AutoPlaces.desired(places, inside, marker, street, stale)

    @Test fun arrivingSharesThePlace() {
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = null))
        // Arriving home from «En la calle» or from another place replaces it.
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = AutoPlaces.STREET_MARKER))
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = "uni"))
    }

    @Test fun aPlaceAlreadySharedIsOnlySentAgainOnceADay() {
        assertEquals(Change.Nothing, desired(listOf("home"), marker = "home"))
        assertEquals(Change.Nothing, desired(listOf("home", "uni"), marker = "uni"))
        assertEquals(Change.Nothing, desired(emptyList(), marker = AutoPlaces.STREET_MARKER, street = street))
        // Shared over a day ago: sent again so it does not expire while the phone stays there.
        assertEquals(Change.Share("En la universidad", "uni"), desired(listOf("home", "uni"), marker = "uni", stale = true))
        assertEquals(street, desired(emptyList(), marker = AutoPlaces.STREET_MARKER, street = street, stale = true))
    }

    @Test fun leavingUpdatesThePlaceEvenIfTheEarlierAttemptFailed() {
        // Left the university: «En la calle» when it is on, otherwise the place is removed.
        assertEquals(street, desired(emptyList(), marker = "uni", street = street))
        assertEquals(Change.Clear, desired(emptyList(), marker = "uni"))
        // A retry after the place was already removed changes nothing.
        assertEquals(Change.Nothing, desired(emptyList(), marker = null))
    }

    @Test fun leavingOnePlaceWhileInAnotherSharesTheOther() {
        assertEquals(Change.Share("En casa", "home"), desired(listOf("home"), marker = "uni"))
        // Inside both, the latest place entered wins.
        assertEquals(Change.Share("En la universidad", "uni"), desired(listOf("home", "uni"), marker = null))
    }

    @Test fun enteringWorkReplacesHomeEvenWhenItsExitWasMissed() {
        val work = AutoPlace("work", "En el trabajo", -12.06, -77.05)
        assertEquals(Change.Share("En el trabajo", "work"),
            AutoPlaces.desired(listOf(home, work), listOf("home", "work"), "home", Change.Nothing))
        // The late home exit must keep the work status; another arrival at home can replace it.
        assertEquals(Change.Nothing,
            AutoPlaces.desired(listOf(home, work), listOf("work"), "work", Change.Nothing))
        assertEquals(Change.Share("En casa", "home"),
            AutoPlaces.desired(listOf(home, work), listOf("work", "home"), "work", Change.Nothing))
    }

    @Test fun aFreshFixOutsideHomeClearsItsStatusWithoutAWorkGeofence() {
        val inside = AutoPlaces.detectedPlaces(listOf(home)) { home.radius + 1 }
        assertTrue(inside.isEmpty())
        assertEquals(Change.Clear, AutoPlaces.desired(listOf(home), inside.toList(), "home", Change.Nothing))
    }

    @Test fun overlappingZonesChooseTheNearestInsteadOfThePreviouslySharedPlace() {
        val inside = AutoPlaces.detectedPlaces(places) { if (it.id == home.id) 140f else 20f }
        assertEquals(Change.Share("En la universidad", "uni"), desired(inside.toList(), marker = "home"))
    }

    @Test fun aWifiFixIsEnoughOnlyWhenItCanTellTheSmallestZoneApart() {
        assertTrue(AutoPlaces.goodEnoughToCheck(35f))
        assertTrue(AutoPlaces.goodEnoughToCheck(AutoPlaces.CHECK_ACCURACY_M))
        assertFalse(AutoPlaces.goodEnoughToCheck(600f))
        assertFalse(AutoPlaces.goodEnoughToCheck(null))
        assertTrue(AutoPlaces.CHECK_ACCURACY_M <= AutoPlaces.RADIUS_CHOICES.min())
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
