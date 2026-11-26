package com.example.findme

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.PlanProposal
import com.example.findme.data.model.ScheduleDto
import com.example.findme.ui.screen.account.AccountActions
import com.example.findme.ui.screen.account.AccountContent
import com.example.findme.ui.screen.account.AccountState
import com.example.findme.ui.theme.NearTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanCancellationUiTest {
    @get:Rule val compose = createComposeRule()
    private val own = AccountProfile("me", "Yo", username = "prueba")
    // The screen shows the day in the profile's zone; with the device's (UTC in CI) the plan lands on
    // another date between midnight UTC and midnight in Lima, and the schedule hides it.
    private val now = ZonedDateTime.now(ZoneId.of(own.timezone))
    private val plan = PlanProposal("shared-plan", "other", "me", "Comer juntos",
        startsAt = now.minusMinutes(1).toInstant().toString(), endsAt = now.plusHours(1).toInstant().toString(),
        status = "accepted", senderScheduleId = "other-copy", recipientScheduleId = "my-copy")
    private val activity = ScheduleDto("my-copy", "me", now.dayOfWeek.value, now.minusMinutes(1).toLocalTime().toString(),
        now.plusHours(1).toLocalTime().toString(), "Comer juntos", "", category = "free",
        endsNextDay = now.plusHours(1).toLocalDate() > now.toLocalDate(), oneOffDate = now.minusMinutes(1).toLocalDate().toString())
    private val state = AccountState(initializing = false, signedIn = true, profile = own,
        profiles = listOf(own, AccountProfile("other", "Ana", username = "ana")),
        plans = listOf(plan), schedules = listOf(activity))

    @Test fun cancellingAnAcceptedPlanRequiresConfirmationForBothSchedules() {
        var cancelled: String? = null
        compose.setContent { NearTheme { AccountContent(state, object : AccountActions {
            override fun cancelPlan(id: String) { cancelled = id }
        }) } }
        compose.onNodeWithTag("near-tab-Personas").performClick()
        compose.onNodeWithText("Cancelar plan").performScrollTo().performClick()
        compose.onNodeWithText("El plan se quitará de los horarios de ambos y se avisará a la otra persona.").assertIsDisplayed()
        compose.runOnIdle { assertNull(cancelled) }
        compose.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertNull(cancelled) }
        compose.onNodeWithText("Cancelar plan").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar").performClick()
        compose.runOnIdle { assertEquals(plan.id, cancelled) }
    }

    @Test fun removingAPlanFromTheScheduleUsesSharedCancellation() {
        var cancelled: String? = null
        var locallyDeleted = false
        compose.setContent { NearTheme { AccountContent(state, object : AccountActions {
            override fun cancelPlan(id: String) { cancelled = id }
            override fun deleteSchedule(id: String) { locallyDeleted = true }
        }) } }
        compose.onNodeWithTag("near-tab-Horario").performClick()
        compose.onNodeWithContentDescription("Opciones de Comer juntos").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar plan").performClick()
        compose.onNodeWithText("El plan se quitará de los horarios de ambos y se avisará a la otra persona.").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").performClick()
        compose.runOnIdle { assertEquals(plan.id, cancelled); assertFalse(locallyDeleted) }
    }
}
