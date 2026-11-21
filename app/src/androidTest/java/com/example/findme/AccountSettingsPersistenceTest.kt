package com.example.findme

import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountSettingsPersistenceTest {
    @Test fun settingsPersistAndNotificationChoicesAreIsolatedByAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AccountSettingsRepository(context)
        val owner = "settings-local-test-owner"
        val other = "settings-local-test-other"
        val previous = repository.settings(owner).first()
        val otherPrevious = repository.settings(other).first()
        try {
            repository.setAlert(owner, AccountAlert.Messages, false)
            repository.setAlert(owner, AccountAlert.Activities, false)
            repository.setTheme(AppTheme.Light)
            val restored = AccountSettingsRepository(context).settings(owner).first()
            assertFalse(restored.messages)
            assertFalse(restored.activities)
            assertEquals(AppTheme.Light, restored.theme)
            assertEquals(otherPrevious.messages, repository.settings(other).first().messages)
            assertEquals(otherPrevious.activities, repository.settings(other).first().activities)
        } finally {
            repository.setAlert(owner, AccountAlert.Messages, previous.messages)
            repository.setAlert(owner, AccountAlert.Activities, previous.activities)
            repository.setTheme(previous.theme)
        }
    }
}
