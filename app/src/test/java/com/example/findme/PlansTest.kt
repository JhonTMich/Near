package com.example.findme

import com.example.findme.data.model.PlanProposal
import com.example.findme.domain.model.Plans
import com.example.findme.push.PushAlerts
import com.example.findme.util.NotificationHelper
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class PlansTest {
    private val now = Instant.parse("2026-11-22T12:00:00Z")

    @Test fun validationMatchesTheDatabase() {
        val start = now.plus(Duration.ofDays(1))
        assertNull(Plans.validate("Vernos", start, start.plusSeconds(3600), now))
        assertNotNull(Plans.validate(" ", start, start.plusSeconds(3600), now))
        assertNotNull(Plans.validate("Vernos", now.minusSeconds(60), now.plusSeconds(3600), now))
        assertNotNull(Plans.validate("Vernos", now.plus(Duration.ofDays(32)), now.plus(Duration.ofDays(32)).plusSeconds(60), now))
        assertNotNull(Plans.validate("Vernos", start, start.plus(Duration.ofHours(13)), now))
        assertNotNull(Plans.validate("Vernos", start, start, now))
    }

    @Test fun timesAreShownInTheReadersZone() {
        val text = Plans.whenText("2026-11-26T23:00:00Z", "2026-11-27T00:00:00Z", ZoneId.of("America/Lima"))
        assertTrue(text, text.endsWith("18:00–19:00"))
        assertTrue(text, text.contains("26"))
    }

    @Test fun onlyUpcomingPlansAreListed() {
        val past = PlanProposal("1", "a", "b", "Antes", startsAt = "2026-11-21T10:00:00Z", endsAt = "2026-11-21T11:00:00Z")
        val next = past.copy(id = "2", title = "Después", startsAt = "2026-11-23T10:00:00Z", endsAt = "2026-11-23T11:00:00Z")
        assertEquals(listOf(next), Plans.upcoming(listOf(next, past), now))
    }

    @Test fun planPushesBecomeAlerts() {
        val proposal = PushAlerts.from(mapOf("type" to "plan", "id" to "p1", "user_id" to "u1", "name" to "Ana",
            "title" to "Vernos", "starts_at" to "2026-11-26T23:00:00Z", "ends_at" to "2026-11-27T00:00:00Z"))!!
        assertEquals("Ana te propone un plan", proposal.title)
        assertTrue(proposal.text.startsWith("Vernos · "))
        assertEquals("u1", proposal.person)
        assertFalse(proposal.requested)
        val answer = PushAlerts.from(mapOf("type" to "plan_answer", "id" to "p1", "user_id" to "u2", "name" to "Beto",
            "title" to "Vernos", "starts_at" to "2026-11-26T23:00:00Z", "accepted" to "true"))!!
        assertEquals("Beto aceptó tu plan", answer.title)
        assertEquals(NotificationHelper.Channel.Requested, answer.channel)
        assertTrue(answer.requested)
        val refused = PushAlerts.from(mapOf("type" to "plan_answer", "id" to "p1", "user_id" to "u2", "name" to "Beto",
            "title" to "Vernos", "starts_at" to "2026-11-26T23:00:00Z", "accepted" to "false"))!!
        assertEquals("Beto no puede", refused.title)
        assertNull(PushAlerts.from(mapOf("type" to "plan", "id" to "p1", "user_id" to "u1", "title" to "Vernos")))
    }

    @Test fun participantsCanCancelAcceptedPlansButOnlyTheSenderWithdrawsPendingOnes() {
        val p = PlanProposal("p", "sender", "recipient", "Vernos", startsAt = now.plusSeconds(3600).toString(),
            endsAt = now.plusSeconds(7200).toString(), status = "accepted", senderScheduleId = "activity-a", recipientScheduleId = "activity-b")
        assertTrue(Plans.canCancel(p, "sender", now))
        assertTrue(Plans.canCancel(p, "recipient", now))
        assertFalse(Plans.canCancel(p, "stranger", now))
        assertFalse(Plans.canCancel(p, "sender", now.plusSeconds(7200)))
        assertFalse(Plans.canCancel(p.copy(status = "cancelled"), "sender", now))
        assertTrue(Plans.canCancel(p.copy(status = "pending"), "sender", now))
        assertFalse(Plans.canCancel(p.copy(status = "pending"), "recipient", now))
        assertEquals(p, Plans.forActivity(listOf(p), "activity-a", "sender"))
        assertEquals(p, Plans.forActivity(listOf(p), "activity-b", "recipient"))
        assertNull(Plans.forActivity(listOf(p), "activity-a", "recipient"))
        assertNull(Plans.forActivity(listOf(p.copy(status = "cancelled")), "activity-a", "sender"))
    }

    @Test fun cancellationPushExplainsThatBothSchedulesWereUpdated() {
        val alert = requireNotNull(PushAlerts.from(mapOf("type" to "plan_cancelled", "id" to "p", "user_id" to "sender",
            "name" to "Ana", "title" to "Vernos", "was_accepted" to "true")))
        assertEquals("Ana canceló el plan", alert.title)
        assertTrue(alert.text.contains("ambos"))
        assertTrue(alert.requested)
        assertEquals(PushAlerts.planId("p"), alert.id)
        assertNull(PushAlerts.from(mapOf("type" to "plan_cancelled", "id" to "p")))
    }
}
