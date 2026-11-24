package com.example.findme.ui.screen.account

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.model.*
import com.example.findme.data.repository.AccountRepository
import com.example.findme.domain.model.validateSchedule
import com.example.findme.domain.model.conflictMessage
import com.example.findme.domain.model.findScheduleConflict
import com.example.findme.domain.model.withPauses
import com.example.findme.domain.model.occurrencesOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.findme.data.local.*
import com.example.findme.data.repository.AccountLiveUpdates
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.domain.model.ScheduleSyncGuard
import com.example.findme.domain.model.ConnectionControls
import com.example.findme.util.NotificationHelper
import com.example.findme.push.PushAlerts
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class AccountState(
    val settings: AccountSettings = AccountSettings(),
    val lastSync: Instant? = null,
    val syncIssue: Boolean = false,
    val peopleRevision: Int = 0,
    val scheduleRevision: Int = 0,
    val initializing: Boolean = true,
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val confirmationEmail: String? = null,
    /** Signed in through a recovery link; the new password has not been chosen yet. */
    val passwordRecovery: Boolean = false,
    /** Address and [android.os.SystemClock.elapsedRealtime] of the last recovery email sent. */
    val recoveryEmailSent: SentEmail? = null,
    /** [android.os.SystemClock.elapsedRealtime] of the last confirmation email sent. */
    val confirmationSentAt: Long? = null,
    val loginRevision: Int = 0,
    val profile: AccountProfile? = null,
    val schedules: List<ScheduleDto> = emptyList(),
    val savedScheduleDraftId: String? = null,
    val statuses: List<StatusOverrideDto> = emptyList(),
    val connections: List<PersonConnection> = emptyList(),
    val profiles: List<AccountProfile> = emptyList(),
    val selectedPerson: AccountProfile? = null,
    val scheduleFollowRequests: List<com.example.findme.data.model.ScheduleFollowRequest>? = null,
    val personSchedules: List<ScheduleDto> = emptyList(),
    val incomingVisibility: Map<String, ConnectionVisibility> = emptyMap(),
    val peopleSchedulesFresh: Boolean = false,
    val peopleStatusesFresh: Boolean = false,
    val personStatuses: List<StatusOverrideDto> = emptyList(),
    val messages: List<QuickMessage> = emptyList(),
    val signals: List<ConnectionSignal> = emptyList(),
    val invitation: ConnectionInvitation? = null,
    val preferences: List<ConnectionPreference> = emptyList(),
    /** Own pauses and those of people who share their schedule. Already applied to the schedules above. */
    val pauses: List<SchedulePause> = emptyList(),
    /** People this account asked to be told about when they are free. */
    /** Your own attendance marks, loaded while attendance is on. */
    val attendance: List<ActivityAttendance> = emptyList(),
    val freeAlerts: List<FreeAlert> = emptyList(),
    /** Plans sent or received by this account. */
    val plans: List<PlanProposal> = emptyList(),
    /** Places saved on this phone for the automatic status; they are never sent anywhere. */
    val autoPlaces: List<com.example.findme.places.AutoPlace> = emptyList(),
    /** Private groups of this account and who is in each. */
    val groups: List<PersonGroup> = emptyList(),
    val groupMembers: List<PersonGroupMember> = emptyList(),
    /** Schedules and statuses of every connected person (not only [selectedPerson]), for the group view. */
    val othersSchedules: List<ScheduleDto> = emptyList(),
    val othersStatuses: List<StatusOverrideDto> = emptyList()
) {
    fun membersOf(groupId: String): List<String> = groupMembers.filter { it.groupId == groupId }.map { it.memberId }
    /** Nicknames, sharing choices and silenced people, as decided by the signed-in person. */
    val controls: ConnectionControls get() = ConnectionControls(profile?.id.orEmpty(), connections, preferences)
}

data class SentEmail(val address: String, val at: Long)

/** Wait before offering to send the same kind of email again; the server also limits how often it sends. */
const val EMAIL_RESEND_SECONDS = 60

private const val SAVE_FAILED = "No se pudieron guardar los datos. Inténtalo de nuevo."
private const val LOAD_FAILED = "No se pudieron cargar tus datos. Inténtalo de nuevo en unos minutos."

class AccountViewModel(application: Application) : AndroidViewModel(application), AccountActions {
    private val repository = AccountRepository()
    private val preferences = AccountSettingsRepository(application)
    private val snapshotMutex = Mutex()
    private val scheduleSync = ScheduleSyncGuard()
    private var baselineOwner: String? = null
    private val messageNotifications = com.example.findme.domain.model.MessageNotificationTracker()
    private var knownStatuses = mutableMapOf<String, String>()
    private var allSchedules: List<ScheduleDto> = emptyList()
    private var allStatuses: List<StatusOverrideDto> = emptyList()
    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.auth.sessionStatus.collect {
                val user = repository.auth.currentUserOrNull()
                // Initializing has no user yet; wait until the SDK finishes restoring its session.
                if (it is io.github.jan.supabase.auth.status.SessionStatus.Initializing) return@collect
                if (user == null) {
                    PendingPasswordRecovery.clear(getApplication())
                    launch { runCatching { com.example.findme.places.AutoPlaces.register(getApplication(), null) } }
                    // Keep the resend countdowns: signing out does not let the server send sooner.
                    _state.value = AccountState(initializing = false,
                        recoveryEmailSent = _state.value.recoveryEmailSent, confirmationSentAt = _state.value.confirmationSentAt)
                    com.example.findme.widget.WidgetStore.clear(getApplication())
                    AccountCache.clear(getApplication())
                }
                else if (_state.value.profile?.id != user.id) {
                    // A recovery link that was opened but never finished still demands the new password.
                    val recovering = PendingPasswordRecovery.isPending(getApplication(), user.id)
                    _state.update { old -> old.copy(initializing = false, signedIn = true,
                        passwordRecovery = old.passwordRecovery || recovering) }
                    // This device receives this account's pushes from now on.
                    launch { com.example.findme.push.PushTokens.register() }
                    launch { loadAutoPlaces(user.id) }
                    // Show the last successful sync at once, then refresh it from the network.
                    restoreCache(user.id)
                    refresh()
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.auth.sessionStatus.collectLatest {
                val id = repository.auth.currentUserOrNull()?.id ?: return@collectLatest
                preferences.settings(id).collect { settings ->
                    if (repository.auth.currentUserOrNull()?.id == id) _state.update { old -> old.copy(settings = settings) }
                }
            }
        }
    }

    /** [failure] is shown for unexpected server errors; reads must not claim that something failed to save. */
    private fun perform(quiet: Boolean = false, failure: String = SAVE_FAILED, action: suspend () -> Unit) {
        if (_state.value.busy) {
            // Say why nothing happened instead of dropping the tap silently. A refresh is simply skipped.
            if (!quiet) _state.update { it.copy(notice = "Espera a que termine la operación en curso e inténtalo de nuevo.") }
            return
        }
        _state.update { it.copy(busy = true, error = null, notice = null, savedScheduleDraftId = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) {
                _state.update { it.copy(error = error.message ?: "Revisa los datos ingresados.") }
            }
            catch (error: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
                val message = when (error.code) {
                    "23505" -> "Ese nombre de usuario ya está ocupado. Elige otro."
                    "23P01" -> "Este horario coincide con otra actividad. Actualiza tu horario y elige otra hora."
                    "FM001" -> "Llegaste al máximo permitido. Elimina elementos que ya no uses e inténtalo de nuevo."
                    "FM002" -> "Demasiados intentos seguidos. Espera unos minutos e inténtalo de nuevo."
                    "FM004" -> "La fecha o las horas de esta actividad no son válidas. Revisa la ocurrencia."
                    "FM003" -> "La zona horaria del teléfono no es válida. Revisa la configuración de fecha y hora."
                    "FM005" -> "Ese horario ya no está libre para quien te propuso el plan. Pídele que proponga otro."
                    "FM006" -> "El contacto dejó de compartir su horario. La sincronización sigue activa. Puedes cerrarla sin guardar una copia."
                    "FM007" -> "La conexión terminó. Ya no se puede aceptar el plan."
                    else -> failure
                }
                // Only the code: the full error carries the request URL with account ids.
                if (message == failure) android.util.Log.w("AccountViewModel", "Server rejected the request (code ${error.code})")
                _state.update { it.copy(error = message) }
            }
            catch (error: Exception) {
                android.util.Log.w("AccountViewModel", "Account operation failed", error)
                _state.update { it.copy(error = "No se pudo completar la operación. Comprueba tu conexión, tus datos y la configuración de cuentas del servidor.") }
            } finally { _state.update { it.copy(busy = false, initializing = false) } }
        }
    }

    override fun requestPasswordReset(email: String) = perform {
        require(com.example.findme.domain.model.Registration.isEmail(email)) { "Introduce un correo válido." }
        repository.requestPasswordReset(email)
        _state.update { it.copy(recoveryEmailSent = SentEmail(email.trim(), android.os.SystemClock.elapsedRealtime()),
            notice = "Si ese correo tiene una cuenta, recibirás un enlace para elegir otra contraseña. " +
            "Ábrelo en este teléfono y revisa también la carpeta de spam.") }
    }
    /** Called when the recovery email's link opens the app. */
    fun completePasswordRecovery(code: String) = perform(failure = LOAD_FAILED) {
        // Set before the session appears, so the app never shows the account behind the new-password page.
        _state.update { it.copy(passwordRecovery = true) }
        try { repository.exchangeRecoveryCode(code) }
        catch (error: Exception) {
            _state.update { it.copy(passwordRecovery = PendingPasswordRecovery.isPending(getApplication(), repository.auth.currentUserOrNull()?.id.orEmpty())) }
            throw error
        }
        PendingPasswordRecovery.mark(getApplication(), repository.userId())
        _state.update { it.copy(signedIn = true, passwordRecovery = true, recoveryEmailSent = null) }
        // The session listener's refresh is skipped while this action is running, so load here.
        load()
    }
    fun recoveryLinkFailed() {
        _state.update { it.copy(error = "El enlace para cambiar la contraseña no es válido o venció. Solicita uno nuevo.") }
    }
    override fun setRecoveredPassword(password: String, confirmation: String) = perform {
        require(password == confirmation) { "Las contraseñas no coinciden." }
        repository.setPassword(password)
        PendingPasswordRecovery.clear(getApplication())
        _state.update { it.copy(passwordRecovery = false, notice = "Contraseña actualizada. Ya puedes usarla para entrar.") }
    }
    /** Leaving without a new password signs out: the link's session must not stand in for the password. */
    override fun cancelPasswordRecovery() = logout()
    override fun login(username: String, password: String) = perform(failure = LOAD_FAILED) {
        require(com.example.findme.domain.model.Username.isValid(username) && password.isNotBlank()) { "Introduce un usuario válido y tu contraseña." }
        repository.signIn(username, password)
        if (repository.auth.currentUserOrNull() == null) {
            _state.update { it.copy(notice = "Revisa tu correo para confirmar la cuenta y después inicia sesión.") }
        } else load()
    }

    private suspend fun <T> readSection(section: String, read: suspend () -> T): T? = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val code = (error as? io.github.jan.supabase.postgrest.exception.PostgrestRestException)?.code
            ?: error.javaClass.simpleName
        android.util.Log.w("AccountViewModel", "Unable to synchronize $section ($code)")
        null
    }

    /** Zone the server last refused, so an unknown device zone is not sent again on every refresh. */
    private var refusedTimezone: String? = null

    /**
     * The profile's zone was fixed when the account was created, so a wrong phone setting or a trip
     * left schedules, reminders and pushes shifted. Keep it equal to the phone's zone.
     */
    private suspend fun followPhoneTimezone(profile: AccountProfile): AccountProfile {
        val zone = ZoneId.systemDefault().id
        if (profile.timezone == zone || zone == refusedTimezone) return profile
        return try { repository.updateTimezone(zone) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
            // The server rejects zones it does not know (FM003); a network failure is retried next refresh.
            android.util.Log.w("AccountViewModel", "Time zone not accepted (code ${error.code})")
            refusedTimezone = zone
            profile
        } catch (error: Exception) {
            android.util.Log.w("AccountViewModel", "Unable to synchronize time zone (${error.javaClass.simpleName})")
            profile
        }
    }

    private suspend fun load() = snapshotMutex.withLock {
        coroutineScope {
            val id = repository.userId()
            val scheduleRevision = scheduleSync.snapshotRevision()
            val profileRequest = async { readSection("profile", repository::ensureProfile) }
            val connectionsRequest = async { readSection("connections", repository::connections) }
            val profilesRequest = async { readSection("people", repository::profiles) }
            val messagesRequest = async { readSection("quick messages", repository::quickMessages) }
            val signalsRequest = async { readSection("inbox", repository::receivedSignals) }
            val schedulesRequest = async { readSection("schedules", repository::accessibleSchedules) }
            val followsRequest = async { readSection("schedule follows", repository::scheduleFollowRequests) }
            val statusesRequest = async { readSection("statuses", repository::accessibleStatuses) }
            val visibilityRequest = async { readSection("incoming visibility", repository::incomingConnectionVisibility) }
            val preferencesRequest = async { readSection("connection preferences", repository::connectionPreferences) }
            // Not part of a complete sync: a server without the pauses table still syncs everything else.
            val pausesRequest = async { readSection("schedule pauses", repository::pauses) }
            val freeAlertsRequest = async { readSection("free alerts", repository::freeAlerts) }
            val plansRequest = async { readSection("plans", repository::plans) }
            val groupsRequest = async { readSection("groups", repository::groups) }
            val groupMembersRequest = async { readSection("group members", repository::groupMembers) }
            val remoteProfile = profileRequest.await()
            val remoteConnections = connectionsRequest.await()
            val remoteProfiles = profilesRequest.await()
            val remoteMessages = messagesRequest.await()
            val remoteSignals = signalsRequest.await()
            val remoteSchedules = schedulesRequest.await()
            val remoteFollows = followsRequest.await()
            val remoteStatuses = statusesRequest.await()
            val remotePreferences = preferencesRequest.await()
            val remoteVisibility = visibilityRequest.await()
            val remotePauses = pausesRequest.await()
            val remoteFreeAlerts = freeAlertsRequest.await()
            val remotePlans = plansRequest.await()
            val remoteGroups = groupsRequest.await()
            val remoteGroupMembers = groupMembersRequest.await()
            if (repository.auth.currentUserOrNull()?.id != id) return@coroutineScope

            val old = _state.value
            val previous = if (old.profile?.id == id) old else null
            val loadedProfile = remoteProfile ?: previous?.profile ?: run {
                _state.update { it.copy(syncIssue = true) }
                return@coroutineScope
            }
            val profile = if (remoteProfile != null) followPhoneTimezone(remoteProfile) else loadedProfile
            val connections = remoteConnections ?: previous?.connections.orEmpty()
            val peopleIds = connections.map { it.otherPerson(id) }.toSet()
            val profiles = (remoteProfiles ?: previous?.profiles.orEmpty()).filter { it.id in peopleIds } + profile
            val visibleIds = profiles.mapTo(mutableSetOf()) { it.id }
            val messages = remoteMessages ?: previous?.messages.orEmpty()
            val signals = remoteSignals ?: previous?.signals.orEmpty()
            val preferences = (remotePreferences ?: previous?.preferences.orEmpty())
                .filter { preference -> connections.any { it.id == preference.connectionId } }
            val complete = listOf(remoteProfile, remoteConnections, remoteProfiles, remoteMessages,
                remoteSignals, remoteSchedules, remoteStatuses, remotePreferences).all { it != null }
            val previousSchedules = if (previous == null) emptyList() else allSchedules
            val previousStatuses = if (previous == null) emptyList() else allStatuses
            // An older response must not undo a confirmed save or resurrect a deleted row.
            val pauses = (remotePauses ?: previous?.pauses.orEmpty()).filter { it.userId in visibleIds }
            val ownSchedules = (if (remoteSchedules != null && scheduleSync.canApplySnapshot(scheduleRevision)) {
                remoteSchedules.filter { it.userId == id }
            } else previous?.schedules.orEmpty()).withPauses(pauses)
            val schedules = (remoteSchedules ?: previousSchedules)
                .filter { it.userId != id && it.userId in visibleIds }.withPauses(pauses) + ownSchedules
            val statuses = (remoteStatuses ?: previousStatuses).filter { it.userId in visibleIds }
            if (baselineOwner != id) {
                baselineOwner = id
                knownStatuses.clear()
            }
            val selected = previous?.selectedPerson?.let { person -> profiles.find { it.id == person.id && it.id != id } }
                ?: profiles.firstOrNull { it.id != id }
            val syncedAt = if (complete) Instant.now() else previous?.lastSync
            allSchedules = schedules
            allStatuses = statuses
            _state.update { it.copy(signedIn = true, profile = profile,
                schedules = ownSchedules, statuses = statuses.filter { it.userId == id },
                connections = connections, profiles = profiles, messages = messages, signals = signals,
                preferences = preferences, pauses = pauses, selectedPerson = selected,
                scheduleFollowRequests = remoteFollows,
                freeAlerts = (remoteFreeAlerts ?: previous?.freeAlerts.orEmpty()).filter { it.targetId in peopleIds },
                plans = (remotePlans ?: previous?.plans.orEmpty()).filter { it.senderId in peopleIds || it.recipientId in peopleIds },
                groups = remoteGroups ?: previous?.groups.orEmpty(),
                groupMembers = (remoteGroupMembers ?: previous?.groupMembers.orEmpty()).filter { it.memberId in peopleIds },
                othersSchedules = schedules.filter { it.userId != id }, othersStatuses = statuses.filter { it.userId != id }, personSchedules = schedules.filter { it.userId == selected?.id },
                personStatuses = statuses.filter { it.userId == selected?.id }, lastSync = syncedAt, syncIssue = !complete,
                incomingVisibility = remoteVisibility.orEmpty().filter { it.personId in peopleIds }.associateBy { it.personId },
                peopleSchedulesFresh = remoteSchedules != null, peopleStatusesFresh = remoteStatuses != null) }
            val current = _state.value
            if (remoteSignals != null && remotePreferences != null && remoteProfiles != null && remoteConnections != null) {
                val newSignals = messageNotifications.update(id, signals, current.settings.messages)
                current.controls.notifiable(newSignals).forEach { signal ->
                    val sender = profiles.find { it.id == signal.senderId }?.let(current.controls::nameOf) ?: "Tu gente"
                    NotificationHelper.showMessageNotification(getApplication(), signal.id, sender, signal.message,
                        NotificationHelper.Reply(id, signal.senderId, messages.map { it.message }.filter { it.trim().length in 1..240 }.distinct().take(5)))
                }
            }
            if (complete || previous != null) saveCurrentCache()
            if (remoteSchedules != null && remoteStatuses != null && remoteConnections != null && remoteProfiles != null) {
                publishWidgetSnapshot()
            }
            if (remoteSchedules != null && remoteStatuses != null && remotePreferences != null) updateStatusNotifications()
        }
    }

    private suspend fun refreshAfterSave(notice: String? = null) {
        val owner = repository.auth.currentUserOrNull()?.id ?: return
        if (notice != null) _state.update { it.copy(notice = notice) }
        if (readSection("after saving") { load() } == null && repository.auth.currentUserOrNull()?.id == owner) {
            _state.update { it.copy(syncIssue = true) }
        }
    }

    private suspend fun saveCurrentCache() {
        val current = _state.value
        val profile = current.profile ?: return
        saveCache(AccountSnapshot(profile.id, profile, current.profiles, allSchedules, allStatuses,
            current.connections, current.messages, current.signals, current.lastSync?.toString().orEmpty(),
            current.preferences, current.pauses))
    }

    private suspend fun saveCache(snapshot: AccountSnapshot) {
        try {
            if (repository.auth.currentUserOrNull()?.id == snapshot.owner) AccountCache.save(getApplication(), snapshot)
            // A logout that happened during the write must not leave this account's data behind.
            if (repository.auth.currentUserOrNull()?.id != snapshot.owner) AccountCache.clear(getApplication())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("AccountViewModel", "Account cache update failed", error) }
    }

    private suspend fun restoreCache(id: String) {
        val cached = try { AccountCache.load(getApplication(), id) } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null } ?: return
        if (repository.auth.currentUserOrNull()?.id != id || _state.value.profile != null) return
        val cutoff = Instant.now().minusSeconds(86400)
        val signals = cached.signals.filter { runCatching { Instant.parse(it.createdAt) > cutoff }.getOrDefault(false) }
        // Messages already seen before closing the app are not notified again.
        messageNotifications.update(id, signals, enabled = false)
        val selected = cached.profiles.firstOrNull { it.id != id }
        allSchedules = cached.schedules
        allStatuses = cached.statuses
        _state.update { it.copy(signedIn = true, initializing = false, profile = cached.profile,
            schedules = cached.schedules.filter { row -> row.userId == id }, statuses = cached.statuses.filter { row -> row.userId == id },
            connections = cached.connections, profiles = cached.profiles, messages = cached.messages, signals = signals,
            preferences = cached.preferences, pauses = cached.pauses,
            selectedPerson = selected, personSchedules = cached.schedules.filter { row -> row.userId == selected?.id },
            personStatuses = cached.statuses.filter { row -> row.userId == selected?.id },
            othersSchedules = cached.schedules.filter { row -> row.userId != id }, othersStatuses = cached.statuses.filter { row -> row.userId != id },
            lastSync = runCatching { Instant.parse(cached.syncedAt) }.getOrNull()) }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun observeWhileVisible(): Unit = coroutineScope {
        launch {
            while (isActive) {
                delay(15_000)
                val cutoff = Instant.now().minusSeconds(86400)
                _state.update { old -> old.copy(signals = old.signals.filter {
                    runCatching { Instant.parse(it.createdAt) > cutoff }.getOrDefault(false)
                }) }
                updateStatusNotifications()
            }
        }
        repository.auth.sessionStatus.collectLatest {
            val id = repository.auth.currentUserOrNull()?.id ?: return@collectLatest
            AccountLiveUpdates.observe(id).sample(500).collect {
                try { load() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _state.update { old -> old.copy(syncIssue = true) } }
            }
        }
    }

    private suspend fun publishWidgetSnapshot() {
        val profile = _state.value.profile ?: return
        if (repository.auth.currentUserOrNull()?.id != profile.id) return
        try {
            com.example.findme.widget.WidgetStore.publish(getApplication(), profile.id,
                _state.value.profiles.filterNot { it.id == profile.id } + profile, allSchedules, allStatuses) {
                repository.auth.currentUserOrNull()?.id == profile.id
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("AccountViewModel", "Widget update failed", error) }
    }

    private fun updateStatusNotifications() {
        val state = _state.value
        val own = state.profile?.id ?: return
        if (repository.auth.currentUserOrNull()?.id != own) return
        val activeIds = state.profiles.map { it.id }.toSet()
        knownStatuses.keys.retainAll(activeIds)
        val controls = state.controls
        state.profiles.filter { it.id != own }.forEach { person ->
            val zone = runCatching { ZoneId.of(person.timezone) }.getOrDefault(ZoneId.systemDefault())
            val now = ZonedDateTime.now(zone)
            val status = StatusRepository.calculateStatus(person.displayName,
                allSchedules.filter { it.userId == person.id }, allStatuses.filter { it.userId == person.id },
                now.dayOfWeek.value, now)
            val key = when (status) {
                is UserCurrentStatus.InClass -> "activity:${status.currentSchedule.id}"
                is UserCurrentStatus.Override -> "status:${status.customLocation}:${status.customMessage}"
                else -> "schedule"
            }
            val previous = knownStatuses.put(person.id, key)
            // A silenced person's changes are still tracked, so unsilencing does not replay them.
            if (previous != null && previous != key && !controls.isMuted(person.id)) {
                val name = controls.nameOf(person)
                when {
                    status is UserCurrentStatus.Override && state.settings.statuses ->
                        NotificationHelper.showStatusNotification(getApplication(), person.id, name,
                            PushAlerts.statusText(status.customLocation, status.customMessage))
                    status is UserCurrentStatus.InClass && state.settings.activities ->
                        NotificationHelper.showStatusNotification(getApplication(), person.id, name,
                            PushAlerts.activityText(status.currentSchedule.title, status.currentSchedule.location), activity = true)
                    previous.startsWith("status:") && state.settings.statuses ->
                        NotificationHelper.showStatusNotification(getApplication(), person.id, name, PushAlerts.STATUS_ENDED)
                }
            }
        }
    }

    fun openPeople() { _state.update { it.copy(peopleRevision = it.peopleRevision + 1) } }
    fun openSchedule() { _state.update { it.copy(scheduleRevision = it.scheduleRevision + 1) } }
    override fun setAlert(alert: AccountAlert, enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch { preferences.setAlert(id, alert, enabled) }
    }
    override fun setTheme(theme: AppTheme) { viewModelScope.launch { preferences.setTheme(theme) } }
    override fun setReminders(enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            preferences.setReminders(id, enabled)
            com.example.findme.reminders.ActivityReminders.reschedule(getApplication())
        }
    }
    override fun setReminderMinutes(minutes: Int) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            preferences.setReminderMinutes(id, minutes)
            com.example.findme.reminders.ActivityReminders.reschedule(getApplication())
        }
    }
    override fun setReminderAlarm(enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            preferences.setReminderAlarm(id, enabled)
            // An alarm is scheduled as a visible alarm clock, a notification as a plain exact alarm.
            com.example.findme.reminders.ActivityReminders.reschedule(getApplication())
            if (!enabled) com.example.findme.reminders.ReminderAlarm.cancel(getApplication())
        }
    }
    override fun setAlarmSound(uri: String?) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch { preferences.setAlarmSound(id, uri) }
    }
    override fun setAlarmVibrate(enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch { preferences.setAlarmVibrate(id, enabled) }
    }
    override fun setAlarmSnoozeMinutes(minutes: Int) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch { preferences.setAlarmSnoozeMinutes(id, minutes) }
    }
    override fun testAlarm() {
        val app = getApplication<android.app.Application>()
        val ringing = com.example.findme.reminders.ReminderAlarm.Ringing.of("test", "Así sonará tu alarma",
            "Prueba de los recordatorios de Near", startsAt = 0L, settings = _state.value.settings)
        com.example.findme.reminders.ReminderAlarm.ring(app, ringing)
    }

    override fun refresh() = perform(quiet = true, failure = LOAD_FAILED) { load() }
    override fun register(name: String, email: String, username: String, password: String, confirmation: String) = perform {
        com.example.findme.domain.model.Registration.error(name, email, username, password, confirmation)?.let { throw IllegalArgumentException(it) }
        com.example.findme.data.repository.AuthorizedSignup.register(name, email, username, password)
        _state.update { it.copy(confirmationEmail = email.trim(), confirmationSentAt = android.os.SystemClock.elapsedRealtime()) }
    }
    override fun resendConfirmation(email: String) = perform {
        require(com.example.findme.domain.model.Registration.isEmail(email)) { "Introduce un correo válido." }
        com.example.findme.data.repository.AuthorizedSignup.resend(email)
        _state.update { it.copy(confirmationEmail = email.trim(), confirmationSentAt = android.os.SystemClock.elapsedRealtime(),
            notice = "Solicitud enviada. Revisa tu correo y la carpeta de spam.") }
    }
    override fun finishRegistration() {
        _state.update { it.copy(confirmationEmail = null, error = null, notice = null, loginRevision = it.loginRevision + 1) }
    }
    override fun saveProfile(name: String, username: String) = perform {
        val profile = repository.saveProfile(name, username)
        _state.update { old -> old.copy(profile = profile,
            profiles = old.profiles.filterNot { it.id == profile.id } + profile,
            notice = "Perfil guardado.") }
        publishWidgetSnapshot()
    }
    override fun logout() = perform {
        // While the session still exists, so the server accepts removing this device.
        com.example.findme.push.PushTokens.unregister()
        forgetAutoPlaces()
        repository.auth.signOut()
        PendingPasswordRecovery.clear(getApplication())
        _state.value = AccountState(initializing = false,
            recoveryEmailSent = _state.value.recoveryEmailSent, confirmationSentAt = _state.value.confirmationSentAt)
        com.example.findme.widget.WidgetStore.clear(getApplication())
    }
    override fun saveSchedules(draftId: String, activities: List<ScheduleDto>) {
        perform {
            require(activities.isNotEmpty()) { "Elige al menos un día." }
            val currentSchedules = _state.value.schedules
            activities.forEach { activity ->
                val zone = runCatching { ZoneId.of(_state.value.profile?.timezone) }.getOrDefault(ZoneId.systemDefault())
                val error = activity.oneOffDate?.let { date ->
                    runCatching { java.time.LocalDate.parse(date) }.getOrNull()
                        ?.let { com.example.findme.domain.model.validateOneOffDate(it, java.time.LocalDate.now(zone)) }
                        ?: if (java.time.LocalDate.parse(date).dayOfWeek.value != activity.dayOfWeek) "Elige una fecha válida." else null
                } ?: validateSchedule(activity.dayOfWeek, activity.startTime, activity.endTime, activity.title, activity.endsNextDay)
                    ?: findScheduleConflict(currentSchedules, activity)?.conflictMessage()
                if (error != null) throw IllegalArgumentException(error)
            }

            val ownerId = repository.userId()
            scheduleSync.beginWrite()
            try {
                val followed = currentSchedules.singleOrNull { it.id == activities.first().id && it.sourceId != null }
                val saved = if (followed == null) {
                    repository.saveSchedules(activities.map { it.copy(userId = ownerId) })
                } else {
                    require(activities.size == 1) { "Edita una sola actividad sincronizada a la vez." }
                    val activity = activities.single()
                    require(activity.oneOffDate == followed.oneOffDate) { "Usa el cambio por fecha para reprogramar esta actividad." }
                    listOf(repository.customizeFollowedActivity(activity, requireNotNull(followed.sourceId)))
                }
                val rows = saved.withPauses(_state.value.pauses)
                if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform

                val ids = rows.map { it.id }.toSet()
                allSchedules = allSchedules.filterNot { it.id in ids } + rows
                _state.update { old -> old.copy(
                    schedules = old.schedules.filterNot { it.id in ids } + rows,
                    savedScheduleDraftId = draftId,
                    notice = if (rows.size == 1) "Actividad guardada." else "Actividad guardada en ${rows.size} días."
                ) }
            } finally { scheduleSync.finishWrite() }
            snapshotMutex.withLock { saveCurrentCache() }
            publishWidgetSnapshot()
        }
    }
    /**
     * Saves the activities chosen from a calendar file. Those that clash with the schedule (or with each
     * other) or are invalid are left out and counted, so one bad event does not block the rest.
     */
    override fun importSchedules(draftId: String, activities: List<ScheduleDto>) = perform {
        require(activities.isNotEmpty()) { "Elige al menos una actividad." }
        val ownerId = repository.userId()
        val zone = runCatching { ZoneId.of(_state.value.profile?.timezone) }.getOrDefault(ZoneId.systemDefault())
        val today = java.time.LocalDate.now(zone)
        // Importing the same file again updates the same rows instead of clashing with them.
        val current = _state.value.schedules.filterNot { row -> activities.any { it.id == row.id } }
        val accepted = mutableListOf<ScheduleDto>()
        var left = 0
        activities.forEach { activity ->
            val invalid = validateSchedule(activity.dayOfWeek, activity.startTime, activity.endTime, activity.title, activity.endsNextDay)
                ?: activity.oneOffDate?.let { com.example.findme.domain.model.validateOneOffDate(java.time.LocalDate.parse(it), today) }
            if (invalid != null || findScheduleConflict(current + accepted, activity) != null) left++
            else accepted += activity.copy(userId = ownerId)
        }
        if (accepted.isEmpty()) throw IllegalArgumentException("Ninguna actividad se pudo importar: todas coinciden con tu horario o no son válidas.")
        scheduleSync.beginWrite()
        try {
            // Weekly and one-off rows go in separate requests, so every row of a request has the same fields.
            val (once, weekly) = accepted.partition { it.isOneOff }
            val rows = ((if (weekly.isNotEmpty()) repository.saveSchedules(weekly) else emptyList()) +
                (if (once.isNotEmpty()) repository.saveSchedules(once) else emptyList())).withPauses(_state.value.pauses)
            if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform
            val ids = rows.map { it.id }.toSet()
            allSchedules = allSchedules.filterNot { it.id in ids } + rows
            _state.update { old -> old.copy(schedules = old.schedules.filterNot { it.id in ids } + rows, savedScheduleDraftId = draftId,
                notice = "Importadas ${rows.size} actividades" + if (left > 0) ". $left no se importaron porque coinciden con tu horario." else ".") }
        } finally { scheduleSync.finishWrite() }
        snapshotMutex.withLock { saveCurrentCache() }
        publishWidgetSnapshot()
    }
    override fun changeScheduleOccurrence(draftId: String, id: String, originalDate: String, change: ScheduleException?) =
        changeOccurrence(draftId, id, originalDate, change, null)

    override fun endActivityNow(id: String, originalDate: String) {
        val zone = runCatching { ZoneId.of(_state.value.profile?.timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = java.time.ZonedDateTime.now(zone)
        val occurrence = _state.value.schedules.occurrencesOn(now.toLocalDate(), zone)
            .firstOrNull { it.schedule.id == id && it.originalDate.toString() == originalDate } ?: return
        val change = com.example.findme.domain.model.EndEarly.change(occurrence, now) ?: return
        changeOccurrence(java.util.UUID.randomUUID().toString(), id, originalDate, change,
            "Actividad terminada por hoy. Puedes restaurarla desde «Solo esta fecha».")
    }

    private fun changeOccurrence(draftId: String, id: String, originalDate: String, change: ScheduleException?, notice: String?) = perform {
        val ownerId = repository.userId()
        scheduleSync.beginWrite()
        try {
            val followed = _state.value.schedules.any { it.id == id && it.sourceId != null }
            val activity = if (followed) repository.changeFollowedOccurrence(id, originalDate, change)
                else repository.changeScheduleOccurrence(id, originalDate, change)
            val updated = listOf(activity).withPauses(_state.value.pauses).single()
            if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform
            allSchedules = allSchedules.map { if (it.id == id) updated else it }
            _state.update { old -> old.copy(
                schedules = old.schedules.map { if (it.id == id) updated else it },
                savedScheduleDraftId = draftId,
                notice = when {
                    change == null -> "Actividad semanal restaurada."
                    change.cancelled -> "Actividad cancelada solo para esa fecha."
                    else -> "Actividad reprogramada solo para esa fecha."
                }
            ) }
        } finally {
            scheduleSync.finishWrite()
        }
        snapshotMutex.withLock { saveCurrentCache() }
        publishWidgetSnapshot()
    }

    override fun addPause(draftId: String, title: String, start: String, end: String) = perform {
        val zone = runCatching { ZoneId.of(_state.value.profile?.timezone) }.getOrDefault(ZoneId.systemDefault())
        com.example.findme.domain.model.validatePause(title,
            runCatching { java.time.LocalDate.parse(start) }.getOrNull(), runCatching { java.time.LocalDate.parse(end) }.getOrNull(),
            java.time.LocalDate.now(zone))?.let { throw IllegalArgumentException(it) }
        val owner = repository.userId()
        val pause = repository.addPause(title, start, end)
        if (repository.auth.currentUserOrNull()?.id != owner) return@perform
        replacePauses(_state.value.pauses.filterNot { it.id == pause.id } + pause)
        _state.update { it.copy(savedScheduleDraftId = draftId, notice = "Tu horario queda en pausa: ${pause.title}.") }
    }
    override fun deletePause(id: String) = perform {
        val owner = repository.userId()
        repository.deletePause(id)
        if (repository.auth.currentUserOrNull()?.id != owner) return@perform
        replacePauses(_state.value.pauses.filterNot { it.id == id })
        _state.update { it.copy(notice = "Pausa eliminada. Tu horario vuelve a aplicarse esas fechas.") }
    }
    override fun notifyWhenFree(personId: String) = perform {
        repository.addFreeAlert(personId)
        val name = _state.value.profiles.find { it.id == personId }?.let(_state.value.controls::nameOf) ?: "esa persona"
        _state.update { old -> old.copy(freeAlerts = old.freeAlerts.filterNot { it.targetId == personId } +
            FreeAlert(repository.userId(), personId, Instant.now().plusSeconds(86400).toString()),
            notice = "Te avisaremos cuando $name quede libre, durante las próximas 24 horas.") }
    }
    override fun cancelFreeAlert(personId: String) = perform {
        repository.removeFreeAlert(personId)
        _state.update { old -> old.copy(freeAlerts = old.freeAlerts.filterNot { it.targetId == personId }, notice = "Aviso cancelado.") }
    }

    override fun proposePlan(draftId: String, personId: String, title: String, location: String, start: Instant, end: Instant) = perform {
        com.example.findme.domain.model.Plans.validate(title, start, end, Instant.now())?.let { throw IllegalArgumentException(it) }
        val owner = repository.userId()
        val plan = repository.proposePlan(personId, title, location, start, end)
        if (repository.auth.currentUserOrNull()?.id != owner) return@perform
        val name = _state.value.profiles.find { it.id == personId }?.let(_state.value.controls::nameOf) ?: "esa persona"
        _state.update { old -> old.copy(plans = old.plans + plan, savedScheduleDraftId = draftId,
            notice = "Propuesta enviada a $name. Te avisaremos cuando responda.") }
    }
    override fun answerPlan(id: String, accept: Boolean) = perform {
        val plan = repository.answerPlan(id, accept)
        _state.update { old -> old.copy(plans = old.plans.map { if (it.id == id) plan else it }) }
        // Accepting added the activity to the schedule; load it.
        refreshAfterSave(if (accept) "Plan aceptado. Ya está en tu horario." else "Respuesta enviada.")
    }
    override fun cancelPlan(id: String) = perform {
        val plan = repository.cancelPlan(id)
        _state.update { old -> old.copy(plans = old.plans.map { if (it.id == id) plan else it }, notice = "Propuesta cancelada.") }
    }

    override fun saveGroup(draftId: String, id: String?, name: String, members: Set<String>) = perform {
        require(members.isNotEmpty()) { "Elige al menos una persona para el grupo." }
        val owner = repository.userId()
        val current = id?.let { _state.value.membersOf(it).toSet() }.orEmpty()
        val group = repository.saveGroup(id, name, members, current)
        if (repository.auth.currentUserOrNull()?.id != owner) return@perform
        _state.update { old -> old.copy(groups = old.groups.filterNot { it.id == group.id } + group,
            groupMembers = old.groupMembers.filterNot { it.groupId == group.id } + members.map { PersonGroupMember(group.id, it) },
            savedScheduleDraftId = draftId, notice = "Grupo «${group.name}» guardado.") }
    }
    override fun deleteGroup(id: String) = perform {
        repository.deleteGroup(id)
        _state.update { old -> old.copy(groups = old.groups.filterNot { it.id == id },
            groupMembers = old.groupMembers.filterNot { it.groupId == id }, notice = "Grupo eliminado.") }
    }
    override fun sendToGroup(groupId: String, message: QuickMessage) = perform {
        val state = _state.value
        val group = state.groups.find { it.id == groupId } ?: return@perform
        val recipients = state.membersOf(groupId).filter { member -> state.connections.any { it.otherPerson(state.profile?.id.orEmpty()) == member } }
        require(recipients.isNotEmpty()) { "Este grupo ya no tiene personas conectadas." }
        repository.sendSignals(recipients, message.message)
        _state.update { it.copy(notice = "Mensaje enviado a ${recipients.size} personas de «${group.name}». Se conserva durante 24 horas.") }
    }

    private suspend fun loadAutoPlaces(owner: String) {
        val places = runCatching { com.example.findme.places.AutoPlaces.places(getApplication(), owner) }.getOrDefault(emptyList())
        if (repository.auth.currentUserOrNull()?.id == owner) _state.update { it.copy(autoPlaces = places) }
        // Opening the app is enough to move a place an older version saved as your status.
        runCatching { com.example.findme.places.AutoPlaces.moveLegacyStatus(getApplication(), owner) }
        runCatching { com.example.findme.places.AutoPlaces.register(getApplication(), owner) }
    }
    /** Saved places are home addresses and the like: they do not outlive the session on this phone. */
    private suspend fun forgetAutoPlaces(owner: String? = repository.auth.currentUserOrNull()?.id) {
        owner ?: return
        // While the session exists (signing out): nobody should keep seeing where you were.
        runCatching { repository.clearPlace() }
        runCatching {
            com.example.findme.places.AutoPlaces.places(getApplication(), owner).forEach {
                com.example.findme.places.AutoPlaces.remove(getApplication(), owner, it.id)
            }
            com.example.findme.places.AutoPlaces.register(getApplication(), null)
        }
    }
    override fun addAutoPlace(label: String, radius: Float, at: com.example.findme.places.AutoPlaces.Found?, category: String?) = perform {
        com.example.findme.places.AutoPlaces.validateLabel(label)?.let { throw IllegalArgumentException(it) }
        val owner = repository.userId()
        val (latitude, longitude) = at?.let { it.latitude to it.longitude }
            ?: com.example.findme.places.AutoPlaces.currentLocation(getApplication())?.let { it.latitude to it.longitude }
            ?: throw IllegalArgumentException("No pudimos saber dónde estás. Activa la ubicación del teléfono e inténtalo de nuevo.")
        val places = com.example.findme.places.AutoPlaces.add(getApplication(), owner, label, latitude, longitude, radius, at?.address, category)
        _state.update { it.copy(autoPlaces = places, notice = "Lugar guardado. Al llegar compartiremos «${label.trim()}».") }
    }
    override suspend fun searchAddress(query: String) =
        runCatching { com.example.findme.places.AutoPlaces.search(getApplication(), query) }.getOrDefault(emptyList())
    override fun setAttendance(enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        val zone = runCatching { ZoneId.of(_state.value.profile?.timezone) }.getOrDefault(ZoneId.systemDefault())
        viewModelScope.launch {
            preferences.setAttendance(id, enabled, java.time.LocalDate.now(zone).toString())
            if (enabled) {
                runCatching { com.example.findme.attendance.AttendanceTracker.check(getApplication()) }
                refreshAttendance()
            } else {
                com.example.findme.attendance.AttendanceCheckWorker.cancel(getApplication())
                _state.update { it.copy(attendance = emptyList()) }
            }
        }
    }
    override fun refreshAttendance() {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            val settings = preferences.settings(id).first()
            val since = settings.attendanceSince?.takeIf { settings.attendance } ?: return@launch
            val records = readSection("attendance") { repository.attendance(since) } ?: return@launch
            if (repository.auth.currentUserOrNull()?.id == id) _state.update { it.copy(attendance = records) }
        }
    }
    override fun markAttendance(scheduleId: String, date: String, present: Boolean?) = perform {
        val id = repository.userId()
        val saved = if (present == null) { repository.clearAttendance(scheduleId, date); null }
            else repository.recordAttendance(scheduleId, date,
                if (present) ActivityAttendance.PRESENT else ActivityAttendance.ABSENT, ActivityAttendance.MANUAL)
        if (repository.auth.currentUserOrNull()?.id != id) return@perform
        _state.update { old -> old.copy(attendance = old.attendance
            .filterNot { it.scheduleId == scheduleId && it.occurrenceDate == date } + listOfNotNull(saved)) }
    }
    override fun setStreetStatus(enabled: Boolean) {
        val id = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            preferences.setStreetStatus(id, enabled)
            // Turning it on checks where the phone is now; turning it off removes only «En la calle».
            runCatching { com.example.findme.places.AutoPlaces.reconcile(getApplication(), locate = enabled) }
        }
    }
    override fun removeAutoPlace(id: String) = perform {
        val places = com.example.findme.places.AutoPlaces.remove(getApplication(), repository.userId(), id)
        // Without places «En la calle» has no meaning: remove it if it was shared.
        if (places.isEmpty()) runCatching { com.example.findme.places.AutoPlaces.reconcile(getApplication()) }
        _state.update { it.copy(autoPlaces = places, notice = "Lugar quitado.") }
    }
    /** After the location permission changes, so the geofences match what Android now allows. */
    override fun refreshAutoPlaces() {
        val owner = repository.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch { loadAutoPlaces(owner) }
    }

    /** Applies the new pauses to every loaded schedule, then saves the cache and refreshes the widget and reminders. */
    private suspend fun replacePauses(pauses: List<SchedulePause>) {
        allSchedules = allSchedules.withPauses(pauses)
        _state.update { old -> old.copy(pauses = pauses, schedules = old.schedules.withPauses(pauses),
            personSchedules = old.personSchedules.withPauses(pauses), othersSchedules = old.othersSchedules.withPauses(pauses)) }
        snapshotMutex.withLock { saveCurrentCache() }
        publishWidgetSnapshot()
    }

    override fun deleteSchedule(id: String) = perform {
        val ownerId = repository.userId()
        scheduleSync.beginWrite()
        try {
            if (_state.value.schedules.any { it.id == id && it.sourceId != null }) {
                repository.deleteFollowedActivity(id)
            } else {
                repository.deleteSchedule(id)
            }
            if (repository.auth.currentUserOrNull()?.id != ownerId) return@perform
            allSchedules = allSchedules.filterNot { it.id == id }
            _state.update { old -> old.copy(
                schedules = old.schedules.filterNot { it.id == id },
                notice = "Actividad eliminada."
            ) }
        } finally { scheduleSync.finishWrite() }
        snapshotMutex.withLock { saveCurrentCache() }
        publishWidgetSnapshot()
    }
    override fun setStatus(text: String, minutes: Long) = perform {
        // First, so removing a place saved as a status by an older version cannot remove this one.
        com.example.findme.places.AutoPlaces.moveLegacyStatus(getApplication(), repository.userId())
        repository.setStatus(text, minutes)
        refreshAfterSave("Estado actualizado.")
    }
    override fun clearStatus() = perform {
        com.example.findme.places.AutoPlaces.moveLegacyStatus(getApplication(), repository.userId())
        repository.clearStatus()
        refreshAfterSave("Se muestra tu horario de nuevo.")
    }
    override fun invite() = perform {
        val invitation = repository.createInvitation()
        _state.update { it.copy(invitation = invitation) }
    }
    override fun accept(code: String) = perform {
        repository.acceptInvitation(code)
        refreshAfterSave("Conexión establecida.")
    }
    override fun disconnect(id: String) = perform {
        val connection = _state.value.connections.find { it.id == id }
        val owner = repository.userId()
        repository.disconnect(id)
        if (repository.auth.currentUserOrNull()?.id != owner) return@perform
        val removed = connection?.otherPerson(owner)
        if (removed != null) {
            allSchedules = allSchedules.filterNot { it.userId == removed }
            allStatuses = allStatuses.filterNot { it.userId == removed }
            _state.update { old ->
                val wasSelected = old.selectedPerson?.id == removed
                old.copy(connections = old.connections.filterNot { it.id == id },
                    incomingVisibility = old.incomingVisibility - removed,
                    profiles = old.profiles.filterNot { it.id == removed },
                    othersSchedules = old.othersSchedules.filterNot { it.userId == removed },
                    othersStatuses = old.othersStatuses.filterNot { it.userId == removed },
                    groupMembers = old.groupMembers.filterNot { it.memberId == removed },
                    preferences = old.preferences.filterNot { it.connectionId == id },
                    selectedPerson = if (wasSelected) null else old.selectedPerson,
                    personSchedules = if (wasSelected) emptyList() else old.personSchedules,
                    personStatuses = if (wasSelected) emptyList() else old.personStatuses)
            }
            readSection("local cache after disconnecting") { AccountCache.clear(getApplication()) }
            publishWidgetSnapshot()
        }
        refreshAfterSave("Conexión eliminada.")
    }
    override suspend fun sharedSchedulesForAvailability(contactId: String): SharedSchedules? {
        val owner = repository.userId()
        val result = repository.sharedSchedulesForAvailability(contactId)
        return result.takeIf { repository.auth.currentUserOrNull()?.id == owner }
    }

    override fun requestScheduleFollow(leaderId: String) = perform {
        repository.requestScheduleFollow(leaderId)
        refreshAfterSave("Solicitud enviada.")
    }

    override fun approveScheduleFollow(id: String) = perform {
        repository.approveScheduleFollow(id)
        refreshAfterSave("Solicitud aceptada.")
    }

    override fun endScheduleFollow(id: String, keepAll: Boolean?) = perform {
        if (keepAll == null) repository.endScheduleFollow(id)
        else repository.detachScheduleFollow(id, keepAll)
        refreshAfterSave("Sincronización finalizada.")
    }

    override fun selectFollowedActivity(requestId: String, sourceId: String, selected: Boolean) = perform {
        repository.selectFollowedActivity(requestId, sourceId, selected)
        refreshAfterSave()
    }

    override fun selectPerson(profile: AccountProfile) = perform {
        val schedules = repository.schedules(profile.id).withPauses(_state.value.pauses)
        val statuses = repository.overrides(profile.id)
        _state.update { it.copy(selectedPerson = profile, personSchedules = schedules, personStatuses = statuses) }
    }
    override fun saveMessage(label: String, message: String) = perform {
        repository.saveQuickMessage(label, message)
        refreshAfterSave("Mensaje rápido creado.")
    }
    override fun deleteMessage(id: String) = perform {
        repository.deleteQuickMessage(id)
        _state.update { old -> old.copy(messages = old.messages.filterNot { it.id == id }) }
        refreshAfterSave("Mensaje rápido eliminado.")
    }
    override fun saveConnectionPreference(preference: ConnectionPreference) = perform {
        repository.saveConnectionPreference(preference)
        _state.update { old -> old.copy(preferences = old.preferences
            .filterNot { it.connectionId == preference.connectionId } + preference) }
        refreshAfterSave("Preferencias guardadas.")
    }
    override fun markSignalRead(id: String) = perform(quiet = true) {
        repository.markSignalRead(id)
        val now = Instant.now().toString()
        _state.update { old -> old.copy(signals = old.signals.map { if (it.id == id && it.readAt == null) it.copy(readAt = now) else it }) }
    }
    override fun deleteSignal(id: String) = perform {
        repository.deleteSignal(id)
        _state.update { old -> old.copy(signals = old.signals.filterNot { it.id == id }, notice = "Mensaje borrado.") }
    }
    override fun changePassword(current: String, new: String, confirmation: String) = perform {
        require(new == confirmation) { "Las contraseñas no coinciden." }
        val username = requireNotNull(_state.value.profile?.username) { "Elige un nombre de usuario antes de cambiar la contraseña." }
        repository.changePassword(username, current, new)
        _state.update { it.copy(notice = "Contraseña actualizada.") }
    }
    override fun deleteAccount(password: String) = perform {
        val owner = repository.userId()
        repository.deleteAccount(password)
        forgetAutoPlaces(owner)
        _state.value = AccountState(initializing = false, notice = "Tu cuenta y tus datos se eliminaron.")
        com.example.findme.widget.WidgetStore.clear(getApplication())
    }
    override fun exportData(): String? {
        val state = _state.value
        val profile = state.profile ?: return null
        return com.example.findme.domain.model.AccountExport.build(profile, state.schedules, state.statuses, state.messages,
            state.connections, state.profiles, state.signals, Instant.now().toString(), state.pauses, state.groups, state.groupMembers, state.attendance)
    }
    override fun exportFinished(saved: Boolean) {
        _state.update { it.copy(notice = if (saved) "Copia de tus datos guardada." else null,
            error = if (saved) null else "No se pudo guardar la copia de tus datos.") }
    }
    override fun send(message: QuickMessage) {
        val recipient = _state.value.selectedPerson ?: return
        perform {
            repository.sendSignal(recipient.id, message.message)
            _state.update { it.copy(notice = "Mensaje enviado a ${_state.value.controls.nameOf(recipient)}. Se conserva durante 24 horas.") }
        }
    }
}
