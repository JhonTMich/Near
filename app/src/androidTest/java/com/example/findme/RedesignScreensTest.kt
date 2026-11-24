package com.example.findme

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.AppTheme
import com.example.findme.data.model.*
import com.example.findme.ui.screen.account.*
import com.example.findme.ui.theme.NearTheme
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** The four main screens with the people and activities of the design, in both themes, saved as images. */
class RedesignScreensTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("America/Lima")
    private val now = ZonedDateTime.now(zone)
    private val day = now.dayOfWeek.value
    private val me = AccountProfile("me", "Johan", username = "jhontmich", timezone = "America/Lima")
    private val gerson = AccountProfile("gerson", "Gerson P", username = "gerson", timezone = "America/Lima")
    private val sandra = AccountProfile("sandra", "Sandra", username = "sandra", timezone = "America/Lima")
    private val research = ScheduleDto("research", "me", day, "08:00", "10:35",
        "Proyecto de Investigación", "G-201", modality = "presencial")
    private val projects = ScheduleDto("projects", "me", day, "16:10", "21:35",
        "Gestión de Proyectos", "G-704/701", modality = "presencial")
    private val sandraWork = ScheduleDto("work", "sandra", day, "00:00", "23:59", "Trabajo", "Oficina", category = "work")
    private val calculus = ScheduleDto("calculus", "gerson", day % 7 + 1, "14:00", "16:00",
        "Cálculo II", "A-101")


    private val state = AccountState(initializing = false, signedIn = true, profile = me,
        schedules = listOf(research, projects),
        statuses = listOf(StatusOverrideDto(id = "home", userId = "me", isActive = true, customLocation = "En casa",
            expiresAt = now.plusMinutes(105).toInstant().toString(), createdAt = now.minusMinutes(140).toInstant().toString())),
        connections = listOf(PersonConnection("c1", "me", "gerson"), PersonConnection("c2", "me", "sandra")),
        profiles = listOf(me, gerson, sandra), selectedPerson = gerson, personSchedules = listOf(calculus),
        othersSchedules = listOf(sandraWork, calculus),
        incomingVisibility = mapOf("gerson" to ConnectionVisibility("gerson", true, true), "sandra" to ConnectionVisibility("sandra", true, true)),
        peopleSchedulesFresh = true, peopleStatusesFresh = true, lastSync = now.toInstant())

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NearRedesign")
        }))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun mainScreensInBothThemes() {
        val theme = mutableStateOf(AppTheme.Dark)
        compose.setContent { NearTheme(theme = theme.value) { AccountContent(state.copy(settings = state.settings.copy(theme = theme.value)), object : AccountActions {}) } }
        listOf(AppTheme.Dark, AppTheme.Light).forEach { current ->
            theme.value = current
            val suffix = current.name.lowercase()
            listOf("Hoy", "Horario", "Personas", "Perfil").forEach { tab ->
                compose.onNodeWithTag("near-tab-$tab").performClick()
                screenshot("$suffix-$tab")
            }
        }
    }
}
