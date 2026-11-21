package com.example.findme.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The whole app is either dark or light; there are no intermediate accents. */
enum class AppTheme(val label: String) { Dark("Oscuro"), Light("Claro") }
enum class AccountAlert { Activities, Statuses, Messages }
data class AccountSettings(
    val activities: Boolean = true,
    val statuses: Boolean = true,
    val messages: Boolean = true,
    val theme: AppTheme = AppTheme.Dark,
    /** A reminder before each of your own activities; off until the person turns it on. */
    val reminders: Boolean = false,
    /** How long before the activity the reminder arrives: one of [REMINDER_CHOICES]. */
    val reminderMinutes: Int = REMINDER_CHOICES.first()
)

val REMINDER_CHOICES = listOf(10, 30, 60)

class AccountSettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore
    private val themeKey = stringPreferencesKey("app_theme")
    private fun alertKey(id: String, alert: AccountAlert) = booleanPreferencesKey("account_${id}_${alert.name}")
    private fun remindersKey(id: String) = booleanPreferencesKey("account_${id}_Reminders")
    private fun reminderMinutesKey(id: String) = intPreferencesKey("account_${id}_reminder_minutes")
    private fun theme(value: String?) = AppTheme.entries.find { it.name == value } ?: AppTheme.Dark
    val theme = store.data.map { theme(it[themeKey]) }.distinctUntilChanged()
    fun settings(id: String) = store.data.map { prefs -> AccountSettings(
        activities = prefs[alertKey(id, AccountAlert.Activities)] ?: true,
        statuses = prefs[alertKey(id, AccountAlert.Statuses)] ?: true,
        messages = prefs[alertKey(id, AccountAlert.Messages)] ?: true,
        theme = theme(prefs[themeKey]),
        reminders = prefs[remindersKey(id)] ?: false,
        reminderMinutes = prefs[reminderMinutesKey(id)]?.takeIf { it in REMINDER_CHOICES } ?: REMINDER_CHOICES.first()
    ) }.distinctUntilChanged()
    suspend fun setAlert(id: String, alert: AccountAlert, enabled: Boolean) {
        store.edit { it[alertKey(id, alert)] = enabled }
    }
    suspend fun setReminders(id: String, enabled: Boolean) { store.edit { it[remindersKey(id)] = enabled } }
    suspend fun setReminderMinutes(id: String, minutes: Int) {
        require(minutes in REMINDER_CHOICES)
        store.edit { it[reminderMinutesKey(id)] = minutes }
    }
    suspend fun setTheme(value: AppTheme) { store.edit { it[themeKey] = value.name } }
}
