package com.example.findme

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.ui.screen.account.AccountActions
import com.example.findme.ui.screen.account.AccountContent
import com.example.findme.ui.screen.account.AccountState
import com.example.findme.ui.screen.account.LoginError
import com.example.findme.ui.theme.NearTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LoginFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val message = "El usuario o la contraseña no son correctos."

    @Test fun loginErrorAppearsBelowSubmitAndAboveTheOpenKeyboard() {
        val state = mutableStateOf(AccountState(initializing = false))
        val actions = object : AccountActions {
            override fun login(username: String, password: String) {
                state.value = state.value.copy(busy = true, error = null)
            }
        }
        compose.setContent { NearTheme { AccountContent(state.value, actions) } }
        compose.onNodeWithText("Nombre de usuario").performScrollTo().performTextInput("prueba_login")
        compose.onNodeWithText("Contraseña").performScrollTo().performClick().performTextInput("clave-de-prueba")
        // Username stays enabled during the request, exercising a keyboard that remains open.
        compose.onNodeWithText("Nombre de usuario").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithText("Entrar").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(busy = false, error = message) }
        compose.waitForIdle()
        compose.onAllNodesWithText(message).assertCountEquals(1)
        val error = compose.onNodeWithTag("login-error").assertIsDisplayed().fetchSemanticsNode()
        val button = compose.onNodeWithText("Entrar").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("The error belongs below the submit button", error.boundsInRoot.top >= button.boundsInRoot.bottom)
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(view))
            assertTrue("The keyboard must remain open", insets.isVisible(WindowInsetsCompat.Type.ime()))
            assertTrue("The error must be above the keyboard",
                error.boundsInWindow.bottom <= view.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom + 1)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = instrumentation.targetContext.contentResolver
        val image = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "login-error-keyboard.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NearTests")
        }))
        requireNotNull(resolver.openOutputStream(image)).use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun theSameErrorShakesAgainAfterRetryAndSettlesInPlace() {
        val error = mutableStateOf<String?>(message)
        compose.mainClock.autoAdvance = false
        compose.setContent { NearTheme { Column(Modifier.padding(24.dp)) { error.value?.let { LoginError(it) } } } }
        compose.mainClock.advanceTimeByFrame()
        val initial = compose.onNodeWithTag("login-error").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(120)
        val shifted = compose.onNodeWithTag("login-error").fetchSemanticsNode().boundsInRoot.left
        assertNotEquals("A failure should shake horizontally", initial, shifted)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(initial, compose.onNodeWithTag("login-error").fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { error.value = null }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("login-error").assertDoesNotExist()
        compose.runOnIdle { error.value = message }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(120)
        assertNotEquals(initial, compose.onNodeWithTag("login-error").fetchSemanticsNode().boundsInRoot.left)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(initial, compose.onNodeWithTag("login-error").fetchSemanticsNode().boundsInRoot.left, 1f)
    }
}
