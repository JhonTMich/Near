package com.example.findme

import com.example.findme.data.local.AccountSettings
import com.example.findme.push.PushAlerts
import com.example.findme.util.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

class InvitationNotificationsTest {
    private val data = mapOf("id" to "request-1", "user_id" to "person-1", "name" to "Ana",
        "title" to "Comer juntos", "starts_at" to "2026-11-26T18:00:00Z", "accepted" to "true")
    private fun alert(type: String) = requireNotNull(PushAlerts.from(data + ("type" to type)))

    @Test fun invitationsAreVisibleWhetherTheAccountScreenIsOpenOrClosed() {
        for (type in listOf("plan", "schedule_follow")) {
            val invitation = alert(type)
            assertTrue(PushAlerts.shouldShow(invitation, AccountSettings(), accountScreenVisible = false))
            assertTrue(PushAlerts.shouldShow(invitation, AccountSettings(), accountScreenVisible = true))
            assertFalse(PushAlerts.shouldShow(invitation, AccountSettings(messages = false), accountScreenVisible = false))
            assertFalse(PushAlerts.shouldShow(invitation, AccountSettings(messages = false), accountScreenVisible = true))
        }
    }

    @Test fun synchronizationRequestsAndApprovalsExplainTheNextStep() {
        val request = alert("schedule_follow")
        assertEquals("Ana quiere sincronizar tu horario", request.title)
        assertTrue(request.text.contains("Personas"))
        assertEquals("person-1", request.person)
        assertEquals(PushAlerts.scheduleFollowId("request-1"), request.id)
        assertNotEquals(PushAlerts.planId("request-1"), request.id)
        val answer = alert("schedule_follow_answer")
        assertTrue(answer.requested)
        assertEquals(NotificationHelper.Channel.Requested, answer.channel)
        assertEquals("Ana aceptó la sincronización", answer.title)
    }

    @Test fun explicitAnswersAreNotHiddenByAnOpenAppOrTheMessagesSwitch() {
        for (type in listOf("plan_answer", "schedule_follow_answer")) {
            assertTrue(PushAlerts.shouldShow(alert(type), AccountSettings(messages = false), accountScreenVisible = true))
        }
    }

    @Test fun ordinaryMessagesStillUseTheirLocalEquivalentInTheForeground() {
        val message = requireNotNull(PushAlerts.from(mapOf("type" to "message", "id" to "m1",
            "sender_id" to "person-1", "message" to "Hola")))
        assertFalse(PushAlerts.shouldShow(message, AccountSettings(), accountScreenVisible = true))
        assertTrue(PushAlerts.shouldShow(message, AccountSettings(), accountScreenVisible = false))
    }

    @Test fun incompleteSynchronizationPayloadsAreIgnored() {
        for (type in listOf("schedule_follow", "schedule_follow_answer")) {
            assertNull(PushAlerts.from((data - "id") + ("type" to type)))
            assertNull(PushAlerts.from((data - "user_id") + ("type" to type)))
            assertNull(PushAlerts.from(data + mapOf("type" to type, "id" to "")))
        }
    }
}
