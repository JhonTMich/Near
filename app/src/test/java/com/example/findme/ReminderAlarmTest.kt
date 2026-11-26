package com.example.findme

import com.example.findme.data.local.ALARM_SNOOZE_CHOICES
import com.example.findme.data.local.AccountSettings
import com.example.findme.reminders.ReminderAlarm
import com.example.findme.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderAlarmTest {
    private val now = Instant.parse("2026-11-17T13:00:00Z")
    private fun minutesLater(minutes: Long) = now.plusSeconds(minutes * 60).toEpochMilli()

    @Test fun theAlarmScreenSaysWhenTheActivityStarts() {
        val label = { at: Long -> com.example.findme.reminders.startsLabel(at, now)?.substringBefore(" · ") }
        assertEquals("Empieza en 10 min", label(minutesLater(10)))
        assertEquals("Empieza en 1 h 30 min", label(minutesLater(90)))
        assertEquals("Empieza en 2 h", label(minutesLater(120)))
        assertEquals("Empieza ahora", label(minutesLater(0)))
        // A test ring has no activity behind it.
        assertNull(com.example.findme.reminders.startsLabel(0, now))
    }

    @Test fun alarmModeIsOptionalAndUsesTheSystemDefaults() {
        val settings = AccountSettings()
        assertFalse(settings.reminderAlarm)
        assertNull(settings.alarmSound)
        assertTrue(settings.alarmVibrate)
        assertEquals(ALARM_SNOOZE_CHOICES.first(), settings.alarmSnoozeMinutes)
    }

    @Test fun ringingCarriesTheChosenSoundVibrationAndSnooze() {
        val settings = AccountSettings(reminderAlarm = true, alarmSound = "content://media/internal/audio/media/12",
            alarmVibrate = false, alarmSnoozeMinutes = 10)
        val ringing = ReminderAlarm.Ringing.of("s1", "Prácticas empieza en 10 min", "A las 08:00", minutesLater(10), settings)
        assertEquals("content://media/internal/audio/media/12", ringing.sound)
        assertFalse(ringing.vibrate)
        assertEquals(10, ringing.snoozeMinutes)
    }

    @Test fun snoozeWaitsTheChosenMinutes() {
        assertEquals(now.plusSeconds(5 * 60), ReminderAlarm.snoozeUntil(now, 5, minutesLater(30)))
    }

    @Test fun snoozeNeverRingsAfterTheActivityStarts() {
        assertEquals(Instant.ofEpochMilli(minutesLater(10)), ReminderAlarm.snoozeUntil(now, 15, minutesLater(10)))
    }

    @Test fun noSnoozeOnceTheActivityStartedOrForATestRing() {
        assertNull(ReminderAlarm.snoozeUntil(now, 5, now.toEpochMilli()))
        assertNull(ReminderAlarm.snoozeUntil(now, 5, now.minusSeconds(60).toEpochMilli()))
        assertNull(ReminderAlarm.snoozeUntil(now, 5, 0L))
    }

    @Test fun alarmsHaveTheirOwnChannel() {
        val alarms = NotificationHelper.Channel.Alarms
        assertTrue(NotificationHelper.Channel.entries.none { it != alarms && it.id == alarms.id })
    }
}
