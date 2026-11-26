package com.example.findme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.AppTheme
import com.example.findme.domain.model.FreeDay
import com.example.findme.domain.model.FreeSlot
import com.example.findme.ui.screen.account.CommonFreeTimeView
import com.example.findme.ui.theme.AppBackground
import com.example.findme.ui.theme.NearTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class CommonFreeTimeUiTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("America/Lima")
    private val monday = LocalDate.of(2026, 11, 16)
    private fun slot(day: LocalDate, from: Int, fromMin: Int, to: Int, toMin: Int = 0) =
        FreeSlot(day.atTime(from, fromMin).atZone(zone), day.atTime(to, toMin).atZone(zone))
    private val days = (0L..6L).map { offset ->
        val day = monday.plusDays(offset)
        FreeDay(day, when (offset) {
            0L -> listOf(slot(day, 16, 30, 18), slot(day, 20, 0, 22))
            1L -> emptyList()
            2L -> listOf(slot(day, 8, 0, 9, 30), slot(day, 13, 0, 14), slot(day, 19, 0, 22))
            else -> listOf(slot(day, 18, 0, 22))
        })
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        TestScreenshots.save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun show(theme: AppTheme, onPropose: (FreeSlot) -> Unit = {}) = compose.setContent {
        var allHours by androidx.compose.runtime.remember { mutableStateOf(false) }
        NearTheme(theme) { Surface(Modifier.fillMaxSize().background(AppBackground)) {
            Column(Modifier.padding(22.dp)) { CommonFreeTimeView(days, allHours, { allHours = it }, onPropose) }
        } }
    }

    @Test fun showsTheNextGapAndTheDaysGaps() {
        var proposed: FreeSlot? = null
        show(AppTheme.Dark) { proposed = it }
        compose.onNodeWithText("Hoy · 16:30").assertIsDisplayed()
        compose.onNodeWithText("16:30 – 18:00").assertIsDisplayed()
        screenshot("free-dark")
        compose.onNodeWithText("17").performClick()
        compose.onNodeWithText("Este día no coinciden").assertIsDisplayed()
        compose.onNodeWithText("18").performClick()
        compose.onNodeWithText("13:00 – 14:00").performClick()
        assertEquals(days[2].slots[1], proposed)
    }

    @Test fun lightTheme() {
        show(AppTheme.Light)
        compose.onNodeWithText("18").performClick()
        screenshot("free-light")
    }
}
