package com.example.findme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.repository.AppRelease
import com.example.findme.data.repository.ReleaseApk
import com.example.findme.domain.model.ReleaseNotes
import com.example.findme.ui.components.AppUpdateDialog
import com.example.findme.ui.screen.UpdateState
import com.example.findme.data.local.AppTheme
import com.example.findme.ui.theme.NearTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppUpdateDialogUiTest {
    @get:Rule val compose = createComposeRule()
    private val notes = ReleaseNotes.plain(
        "- Tu lugar detectado se actualiza al momento y, al guardar un lugar, el formulario se cierra para confirmar que quedó guardado.\n" +
        "- Si algo falla al iniciar sesión, el error aparece junto al botón Entrar, con una pequeña sacudida.\n" +
        "- Recibes un aviso cuando alguien te pide sincronizar o aprueba tu solicitud, y ves las propuestas de planes.\n" +
        "- Puedes cancelar un plan aceptado desde Personas u Horario: se quitan las actividades de ambos.")
    private val state = UpdateState(release = AppRelease("v1.9.5", ReleaseApk("Near.apk", "", 1, ""), notes),
        message = "Hay una actualización de Near disponible.", showDialog = true)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        TestScreenshots.save(name, compose.onAllNodes(isDialog()).onFirst().captureToImage().asAndroidBitmap())
    }

    @Test fun showsShortNewsThatOpenOnATap() {
        compose.setContent { NearTheme { AppUpdateDialog(state, {}, {}, {}) } }
        compose.onNodeWithText("Near 1.9.5").assertIsDisplayed()
        compose.onNodeWithText("4 novedades").assertIsDisplayed()
        compose.onNodeWithText("Ver 1 más").assertIsDisplayed()
        compose.onNodeWithText("Al guardar un lugar", substring = true).assertDoesNotExist()
        screenshot("update-dialog-dark")
        compose.onNodeWithText("Tu lugar detectado se actualiza al momento").performClick()
        compose.onNodeWithText("Al guardar un lugar", substring = true).assertIsDisplayed()
        screenshot("update-dialog-open")
    }

    @Test fun lightThemeAndDownload() {
        var current by androidx.compose.runtime.mutableStateOf(state)
        compose.setContent { NearTheme(AppTheme.Light) { AppUpdateDialog(current, {}, {}, {}) } }
        screenshot("update-dialog-light")
        current = state.copy(downloading = true, progress = .42f)
        compose.onNodeWithText("42%").assertIsDisplayed()
        screenshot("update-dialog-download")
    }
}
