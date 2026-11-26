package com.example.findme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.AppTheme
import com.example.findme.data.model.ScheduleDto
import com.example.findme.ui.screen.account.FollowActivitiesView
import com.example.findme.ui.theme.AppBackground
import com.example.findme.ui.theme.NearTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class FollowActivitiesUiTest {
    @get:Rule val compose = createComposeRule()
    private fun activity(id: String, title: String, day: Int, start: String, end: String, category: String, location: String = "") =
        ScheduleDto(id, dayOfWeek = day, startTime = start, endTime = end, title = title, location = location, category = category)
    private val activities = listOf(
        activity("iot", "IoT", 1, "15:00", "17:00", "university", "Aula 204"),
        activity("calc", "Cálculo II", 1, "08:00", "10:00", "university", "Aula 101"),
        activity("job", "Turno en la tienda", 2, "18:00", "22:00", "work"),
        activity("gym", "Gimnasio", 3, "07:00", "08:00", "free"),
        activity("web", "Desarrollo web", 3, "10:00", "12:00", "university", "Lab 3"))

    private fun screenshot(name: String) {
        compose.waitForIdle()
        TestScreenshots.save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test fun tapsOnlyChangeTheDraftUntilApplied() {
        val applied = mutableListOf<Pair<Set<String>, Set<String>>>()
        compose.setContent { NearTheme { Surface(Modifier.fillMaxSize().background(AppBackground)) {
            Column(Modifier.padding(22.dp)) {
                FollowActivitiesView(activities, setOf("iot", "job"), busy = false, error = null,
                    onApply = { add, remove -> applied += add to remove }, onDone = {})
            }
        } } }
        compose.onNodeWithText("Sin cambios").assertIsNotEnabled()
        compose.onNodeWithText("Desarrollo web").performClick()
        compose.onNodeWithText("Gimnasio").performClick()
        compose.onNodeWithText("Turno en la tienda").performClick()
        // Nothing is saved while choosing.
        assertEquals(0, applied.size)
        compose.onNodeWithText("3 de 5 elegidas").assertIsDisplayed()
        screenshot("follow-dark")
        compose.onNodeWithText("Aplicar  +2 −1").performClick()
        assertEquals(listOf(setOf("web", "gym") to setOf("job")), applied)
    }

    @Test fun lightTheme() {
        compose.setContent { NearTheme(AppTheme.Light) { Surface(Modifier.fillMaxSize().background(AppBackground)) {
            Column(Modifier.padding(22.dp)) {
                FollowActivitiesView(activities, setOf("iot", "web"), busy = false, error = null, onApply = { _, _ -> }, onDone = {})
            }
        } } }
        compose.onNodeWithText("Cálculo II").performClick()
        screenshot("follow-light")
    }
}
