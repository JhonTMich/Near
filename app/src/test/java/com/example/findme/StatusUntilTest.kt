package com.example.findme

import com.example.findme.domain.model.StatusUntil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class StatusUntilTest {
    private val now = ZonedDateTime.of(2026, 11, 22, 14, 20, 0, 0, ZoneId.of("America/Lima"))

    @Test fun lengthUntilALaterTimeToday() {
        assertEquals(130L, StatusUntil.minutes(LocalTime.of(16, 30), now))
    }

    @Test fun aPassedTimeMeansTomorrow() {
        assertEquals(19 * 60L + 40, StatusUntil.minutes(LocalTime.of(10, 0), now))
        assertEquals(1440L, StatusUntil.minutes(LocalTime.of(14, 20), now))
    }

    @Test fun suggestionIsTheNextHalfHourAtLeastThirtyMinutesAway() {
        assertEquals(LocalTime.of(15, 0), StatusUntil.suggestion(now))
        assertEquals(LocalTime.of(15, 0), StatusUntil.suggestion(now.withMinute(30)))
    }

    @Test fun labels() {
        assertEquals("45 min", StatusUntil.lengthLabel(45))
        assertEquals("2 h", StatusUntil.lengthLabel(120))
        assertEquals("2 h 10 min", StatusUntil.lengthLabel(130))
    }
}
