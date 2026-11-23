package com.example.findme.ui.screen.account

import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ConnectionPreference
import com.example.findme.data.model.QuickMessage
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.ScheduleException
import com.example.findme.data.model.SharedSchedules
import com.example.findme.data.local.AccountAlert
import com.example.findme.data.local.AppTheme

/** UI actions kept separate so the account screens can be previewed without a live session. */
interface AccountActions {
    fun refresh() {}
    fun login(username: String, password: String) {}
    fun requestPasswordReset(email: String) {}
    fun setRecoveredPassword(password: String, confirmation: String) {}
    fun cancelPasswordRecovery() {}
    fun register(name: String, email: String, username: String, password: String, confirmation: String) {}
    fun resendConfirmation(email: String) {}
    fun finishRegistration() {}
    fun logout() {}
    fun saveProfile(name: String, username: String) {}
    fun setAlert(alert: AccountAlert, enabled: Boolean) {}
    fun setTheme(theme: AppTheme) {}
    fun setReminders(enabled: Boolean) {}
    fun setReminderMinutes(minutes: Int) {}
    fun setReminderAlarm(enabled: Boolean) {}
    /** A system sound picked with Android's sound picker; null goes back to the default alarm. */
    fun setAlarmSound(uri: String?) {}
    fun setAlarmVibrate(enabled: Boolean) {}
    fun setAlarmSnoozeMinutes(minutes: Int) {}
    /** Rings the alarm now with the chosen sound, so the person can hear it before relying on it. */
    fun testAlarm() {}
    fun saveSchedules(draftId: String, activities: List<ScheduleDto>) {}
    fun changeScheduleOccurrence(draftId: String, id: String, originalDate: String, change: ScheduleException?) {}
    fun deleteSchedule(id: String) {}
    fun importSchedules(draftId: String, activities: List<ScheduleDto>) {}
    /** [start] and [end] are inclusive "yyyy-MM-dd" dates in the owner's zone. */
    fun addPause(draftId: String, title: String, start: String, end: String) {}
    fun deletePause(id: String) {}
    fun notifyWhenFree(personId: String) {}
    fun cancelFreeAlert(personId: String) {}
    fun proposePlan(draftId: String, personId: String, title: String, location: String, start: java.time.Instant, end: java.time.Instant) {}
    fun answerPlan(id: String, accept: Boolean) {}
    fun cancelPlan(id: String) {}
    /** Saves the phone's current location as a place that shares [label] on arrival. */
    fun addAutoPlace(label: String) = addAutoPlace(label, com.example.findme.places.AutoPlaces.RADIUS, null)
    /** Saves [at] (an address found with [searchAddress]), or the current location when null, as a zone of [radius] metres. */
    fun addAutoPlace(label: String, radius: Float, at: com.example.findme.places.AutoPlaces.Found?) {}
    suspend fun searchAddress(query: String): List<com.example.findme.places.AutoPlaces.Found> = emptyList()
    /** Shares «En la calle» outside every saved place when there is no activity in progress. */
    fun setStreetStatus(enabled: Boolean) {}
    fun removeAutoPlace(id: String) {}
    fun refreshAutoPlaces() {}
    /** Creates the group when [id] is null, or renames it; its members become exactly [members]. */
    fun saveGroup(draftId: String, id: String?, name: String, members: Set<String>) {}
    fun deleteGroup(id: String) {}
    fun sendToGroup(groupId: String, message: QuickMessage) {}
    fun setStatus(text: String, minutes: Long) {}
    fun clearStatus() {}
    fun invite() {}
    fun accept(code: String) {}
    fun disconnect(id: String) {}
    suspend fun sharedSchedulesForAvailability(contactId: String): SharedSchedules? = null
    fun requestScheduleFollow(leaderId: String) {}
    fun approveScheduleFollow(id: String) {}
    fun endScheduleFollow(id: String, keepAll: Boolean?) {}
    fun selectFollowedActivity(requestId: String, sourceId: String, selected: Boolean) {}
    fun selectPerson(profile: AccountProfile) {}
    fun saveMessage(label: String, message: String) {}
    fun deleteMessage(id: String) {}
    fun send(message: QuickMessage) {}
    fun saveConnectionPreference(preference: ConnectionPreference) {}
    fun markSignalRead(id: String) {}
    fun deleteSignal(id: String) {}
    fun changePassword(current: String, new: String, confirmation: String) {}
    fun deleteAccount(password: String) {}
    /** JSON copy of the signed-in account's data, or null when nothing is loaded. */
    fun exportData(): String? = null
    fun exportFinished(saved: Boolean) {}
}
