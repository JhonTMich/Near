package com.example.findme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.dataStore
import com.example.findme.places.AutoPlace
import com.example.findme.places.AutoPlaces
import com.example.findme.ui.screen.account.AccountActions
import com.example.findme.ui.screen.account.AccountState
import com.example.findme.ui.screen.account.AutoPlaceSheet
import com.example.findme.ui.theme.NearTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AutoPlaceSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun removingTheCurrentPlaceRetainsThePendingServerRemoval() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = "place-storage-test"
        val keys = listOf("auto_places_", "auto_inside_", "auto_status_", "auto_status_at_")
            .map { stringPreferencesKey(it + owner) }
        val original = context.dataStore.data.first()
        try {
            context.dataStore.edit { prefs -> keys.forEach { prefs.remove(it) } }
            val place = AutoPlaces.add(context, owner, "En casa", -12.05, -77.04).single()
            context.dataStore.edit {
                it[keys[1]] = place.id
                it[keys[2]] = place.id
            }
            assertTrue(AutoPlaces.remove(context, owner, place.id).isEmpty())
            assertTrue(AutoPlaces.places(context, owner).isEmpty())
            assertTrue(AutoPlaces.inside(context, owner).isEmpty())
            val marker = context.dataStore.data.first()[keys[2]]
            assertEquals(place.id, marker)
            assertEquals(AutoPlaces.Change.Clear,
                AutoPlaces.desired(emptyList(), emptyList(), marker, AutoPlaces.Change.Nothing))
        } finally {
            context.dataStore.edit { prefs -> keys.forEach { key ->
                original[key]?.let { prefs[key] = it } ?: prefs.remove(key)
            } }
        }
    }

    @Test fun savingClosesTheSheetAfterThePlaceIsStored() {
        val state = mutableStateOf(AccountState(initializing = false))
        val visible = mutableStateOf(true)
        var saves = 0
        val actions = object : AccountActions {
            override fun addAutoPlace(label: String, radius: Float, at: AutoPlaces.Found?, category: String?) {
                saves++
                state.value = state.value.copy(busy = true)
            }
        }
        compose.setContent {
            NearTheme { if (visible.value) AutoPlaceSheet(state.value, actions) { visible.value = false } }
        }
        compose.onNodeWithText("Guardar lugar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves); assertTrue(visible.value) }
        compose.runOnIdle {
            state.value = state.value.copy(busy = false,
                autoPlaces = listOf(AutoPlace("home", "En casa", -12.05, -77.04)), notice = "Lugar guardado.")
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(visible.value) }
        compose.onNodeWithText("Guardar lugar").assertDoesNotExist()
    }

    @Test fun failureKeepsTheFormOpenAndAllowsRetry() {
        val state = mutableStateOf(AccountState(initializing = false))
        var dismissed = false
        var saves = 0
        val actions = object : AccountActions {
            override fun addAutoPlace(label: String, radius: Float, at: AutoPlaces.Found?, category: String?) {
                saves++
                state.value = state.value.copy(error = "No pudimos saber dónde estás.")
            }
        }
        compose.setContent { NearTheme { AutoPlaceSheet(state.value, actions) { dismissed = true } } }
        compose.onNodeWithText("Guardar lugar").performScrollTo().performClick()
        compose.onNodeWithText("No pudimos saber dónde estás.").assertExists()
        compose.onNodeWithText("Guardar lugar").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, saves); assertFalse(dismissed) }
    }
}
