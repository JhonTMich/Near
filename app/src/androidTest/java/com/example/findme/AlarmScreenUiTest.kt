package com.example.findme

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.AppTheme
import com.example.findme.reminders.AlarmScreen
import com.example.findme.reminders.ReminderAlarm
import com.example.findme.ui.theme.NearTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AlarmScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val ringing = ReminderAlarm.Ringing("iot", "IoT empieza en 10 min", "A las 15:00 · Aula 204",
        System.currentTimeMillis() + 10 * 60_000 + 30_000, null, false, 5)

    private fun screenshot(name: String) {
        // Animations run on the test clock: advance into the middle of a ring for the picture.
        compose.mainClock.advanceTimeBy(400)
        TestScreenshots.save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test fun showsWhatStartsAndBothActions() {
        var stopped = 0; var snoozed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { NearTheme { AlarmScreen(ringing, { stopped++ }, { snoozed++ }) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("IoT empieza en 10 min").assertExists()
        compose.onNodeWithText("Empieza en 10 min", substring = true).assertExists()
        screenshot("alarm-dark")
        compose.onNodeWithText("Posponer 5 min").performClick()
        compose.onNodeWithText("Detener").performClick()
        assertEquals(1 to 1, stopped to snoozed)
    }

    @Test fun lightTheme() {
        compose.mainClock.autoAdvance = false
        compose.setContent { NearTheme(AppTheme.Light) { AlarmScreen(ringing, {}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        screenshot("alarm-light")
    }
}
