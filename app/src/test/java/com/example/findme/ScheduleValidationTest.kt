package com.example.findme

import com.example.findme.domain.model.validateSchedule
import com.example.findme.domain.model.shiftScheduleEnd
import com.example.findme.domain.model.formatScheduleTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleValidationTest {
    @Test fun displaysNoonAndMidnightInTwelveHourFormat() {
        assertEquals("12:00 a. m.", formatScheduleTime("00:00"))
        assertEquals("12:00 p. m.", formatScheduleTime("12:00:00"))
        assertEquals("9:01 p. m.", formatScheduleTime("21:01:00.000000"))
        assertEquals("12:00 a. m.", formatScheduleTime("24:00:00"))
    }

    @Test fun acceptsAValidSameDayActivity() {
        assertNull(validateSchedule(1, "09:00", "10:30", "Trabajo"))
        assertNull(validateSchedule(7, "00:00", "23:59", "Actividad"))
    }

    @Test fun rejectsInvalidDaysAndMissingTitle() {
        assertNotNull(validateSchedule(0, "09:00", "10:00", "Actividad"))
        assertNotNull(validateSchedule(8, "09:00", "10:00", "Actividad"))
        assertNotNull(validateSchedule(1, "09:00", "10:00", "  "))
        assertNotNull(validateSchedule(1, "09:00", "10:00", "a".repeat(121)))
    }

    @Test fun rejectsMalformedTimesAndUnsupportedOvernightIntervals() {
        assertNotNull(validateSchedule(1, "25:00", "26:00", "Actividad"))
        assertNotNull(validateSchedule(1, "9am", "10:00", "Actividad"))
        assertNotNull(validateSchedule(1, "10:00", "10:00", "Actividad"))
        assertNotNull(validateSchedule(1, "23:00", "01:00", "Actividad"))
    }

    @Test fun movingTheStartKeepsTheDuration() {
        assertEquals("12:30" to false, shiftScheduleEnd("09:00", "10:30", false, "11:00"))
        assertEquals("00:30" to true, shiftScheduleEnd("09:00", "10:00", false, "23:30"))
        assertEquals("00:00" to true, shiftScheduleEnd("09:00", "10:00", false, "23:00"))
        assertEquals("09:00" to true, shiftScheduleEnd("19:00", "07:00", true, "21:00"))
        assertEquals("20:00" to false, shiftScheduleEnd("19:00", "07:00", true, "08:00"))
    }

    @Test fun leavesTheEndAloneWhenTheRangeIsInvalid() {
        assertNull(shiftScheduleEnd("10:00", "09:00", false, "11:00"))
        assertNull(shiftScheduleEnd("x", "10:00", false, "11:00"))
    }
}
