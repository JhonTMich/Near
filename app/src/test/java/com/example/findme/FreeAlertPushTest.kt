package com.example.findme

import com.example.findme.data.local.AccountSettings
import com.example.findme.push.PushAlerts
import com.example.findme.util.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

class FreeAlertPushTest {
    @Test fun freePushBecomesARequestedAlert() {
        val alert = PushAlerts.from(mapOf("type" to "free", "user_id" to "u1", "name" to "Ana"))!!
        assertEquals("Ana", alert.title)
        assertEquals(PushAlerts.NOW_FREE, alert.text)
        assertEquals("u1", alert.person)
        assertEquals(NotificationHelper.Channel.Requested, alert.channel)
        assertTrue(alert.requested)
        assertNull(PushAlerts.from(mapOf("type" to "free", "name" to "Ana")))
    }

    @Test fun requestedAlertsIgnoreTheSwitches() {
        val alert = PushAlerts.from(mapOf("type" to "free", "user_id" to "u1", "name" to "Ana"))!!
        val allOff = AccountSettings(activities = false, statuses = false, messages = false)
        assertTrue(PushAlerts.allowed(alert, allOff))
        val activity = PushAlerts.from(mapOf("type" to "activity", "user_id" to "u1", "title" to "Clase"))!!
        assertFalse(PushAlerts.allowed(activity, allOff))
    }
}
