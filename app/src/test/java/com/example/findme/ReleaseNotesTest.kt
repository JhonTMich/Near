package com.example.findme

import com.example.findme.data.repository.AppUpdateRepository
import com.example.findme.domain.model.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test fun markdownBecomesReadablePlainText() {
        val markdown = "## Near 1.8.4\r\n\r\nLanzamiento con el arreglo del inicio de sesión (JhonTMich/Near#72).\r\n\r\n" +
            "- **Entrar** ya no se desplaza.\r\n* Ver [la guía](https://example.test) en `Perfil`."
        assertEquals("Near 1.8.4\n\nLanzamiento con el arreglo del inicio de sesión.\n\n• Entrar ya no se desplaza.\n• Ver la guía en Perfil.",
            ReleaseNotes.plain(markdown))
    }
    @Test fun genericOrMissingNotesAreNotShown() {
        assertEquals("", ReleaseNotes.plain("Actualización de Near 1.8.3."))
        assertEquals("", ReleaseNotes.plain(null))
    }
    @Test fun longNotesAreCutAtALine() {
        val text = ReleaseNotes.plain(List(60) { "- " + "x".repeat(40) }.joinToString("\n"))
        assertTrue(text.length <= ReleaseNotes.MAX_CHARS + 2)
        assertTrue(text.endsWith("\n…"))
    }
    @Test fun parsedReleaseCarriesItsNotes() {
        val asset = """{"name":"Near-1.3.apk","browser_download_url":"https://github.com/JhonTMich/Near/releases/download/v1.3/Near-1.3.apk","size":100,"digest":"sha256:${"ab".repeat(32)}","state":"uploaded"}"""
        val body = """{"tag_name":"v1.3","body":"- Arreglo","draft":false,"prerelease":false,"assets":[$asset]}"""
        assertEquals("• Arreglo", AppUpdateRepository().parseRelease(body, "1.2")!!.notes)
    }
}
