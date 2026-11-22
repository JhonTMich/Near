package com.example.findme.data.repository

import com.example.findme.data.model.*
import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import com.example.findme.domain.model.Username
import com.example.findme.domain.model.InvitationCode

/** The new account model is isolated from the legacy family-code tables. */
class AccountRepository {
    private val client get() = SupabaseClientProvider.client
    val auth get() = client.auth
    fun userId(): String = requireNotNull(auth.currentUserOrNull()?.id) { "Inicia sesión para continuar." }

    suspend fun signIn(username: String, password: String) {
        auth.importSession(UsernameLogin.signIn(username, password))
    }

    /** Re-checks the current password with a fresh login before setting the new one. */
    suspend fun changePassword(username: String, current: String, new: String) {
        require(new.length in 8..128) { "La nueva contraseña debe tener entre 8 y 128 caracteres." }
        require(new != current) { "La nueva contraseña debe ser distinta de la actual." }
        val session = try { UsernameLogin.signIn(username, current) } catch (wrong: IllegalArgumentException) {
            if (wrong.message == "Usuario o contraseña incorrectos.") throw IllegalArgumentException("La contraseña actual no es correcta.")
            throw wrong
        }
        require(session.user?.id == null || session.user?.id == userId()) { "La sesión cambió. Vuelve a intentarlo." }
        auth.importSession(session)
        auth.updateUser { password = new }
    }

    suspend fun deleteAccount(password: String) {
        val token = requireNotNull(auth.currentAccessTokenOrNull()) { "Inicia sesión para continuar." }
        AccountDeletion.delete(token, password)
        // The server already revoked the account; only forget the local session.
        auth.clearSession()
    }

    suspend fun ensureProfile(): AccountProfile {
        val id = userId()
        val existing = client.postgrest["account_profiles"].select { filter { eq("id", id) } }
            .decodeList<AccountProfile>().firstOrNull()
        if (existing != null) return existing
        val user = requireNotNull(auth.currentUserOrNull())
        val name = user.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty()
            .ifBlank { "Mi cuenta" }.take(80)
        val profile = AccountProfile(id, name, ZoneId.systemDefault().id)
        client.postgrest["account_profiles"].insert(profile)
        return profile
    }

    suspend fun profiles() = client.postgrest["account_profiles"].select().decodeList<AccountProfile>()
    suspend fun saveProfile(name: String, username: String): AccountProfile {
        val trimmed = name.trim()
        require(trimmed.length in 1..80 && !trimmed.equals("Mi cuenta", ignoreCase = true)) {
            "Introduce tu nombre, de 1 a 80 caracteres."
        }
        require(Username.isValid(username)) { "Usa entre 3 y 24 letras sin tildes, números o guion bajo para tu usuario." }
        return client.postgrest["account_profiles"].update(buildJsonObject {
            put("display_name", trimmed)
            put("username", Username.normalize(username))
        }) {
            filter { eq("id", userId()) }
            select()
        }.decodeSingle<AccountProfile>()
    }
    /** Schedules, reminders and the server's activity pushes all read the profile's zone; it follows the phone's. */
    suspend fun updateTimezone(zone: String): AccountProfile =
        client.postgrest["account_profiles"].update(buildJsonObject { put("timezone", zone) }) {
            filter { eq("id", userId()) }
            select()
        }.decodeSingle<AccountProfile>()

    suspend fun incomingConnectionVisibility() =
        client.postgrest.rpc("incoming_connection_visibility").decodeList<ConnectionVisibility>()

    suspend fun connections() = client.postgrest["person_connections"].select().decodeList<PersonConnection>()
    suspend fun scheduleFollowRequests(): List<ScheduleFollowRequest> =
        client.postgrest["schedule_follow_requests"].select().decodeList<ScheduleFollowRequest>()

    suspend fun requestScheduleFollow(leaderId: String): ScheduleFollowRequest =
        client.postgrest["schedule_follow_requests"].insert(buildJsonObject {
            put("follower_id", userId())
            put("leader_id", leaderId)
        }) { select() }.decodeSingle<ScheduleFollowRequest>()

    suspend fun approveScheduleFollow(requestId: String): ScheduleFollowRequest =
        client.postgrest["schedule_follow_requests"].update(buildJsonObject {
            put("approved", true)
        }) {
            filter { eq("id", requestId) }
            select()
        }.decodeSingle<ScheduleFollowRequest>()

    suspend fun endScheduleFollow(requestId: String) {
        client.postgrest["schedule_follow_requests"].delete {
            filter { eq("id", requestId) }
        }
    }

    suspend fun schedules(id: String) = client.postgrest["personal_schedule"].select { filter { eq("user_id", id) } }.decodeList<ScheduleDto>()
    suspend fun overrides(id: String) = client.postgrest["personal_status"].select { filter { eq("user_id", id) } }.decodeList<StatusOverrideDto>()
    suspend fun accessibleSchedules() = client.postgrest["personal_schedule"].select().decodeList<ScheduleDto>()
    suspend fun accessibleStatuses() = client.postgrest["personal_status"].select().decodeList<StatusOverrideDto>()

    suspend fun saveSchedules(activities: List<ScheduleDto>): List<ScheduleDto> {
        val ownerId = userId()
        require(activities.isNotEmpty() && activities.all { it.userId == ownerId })
        return client.postgrest["personal_schedule"].upsert(activities.map { it.copy(occurrenceExceptions = emptyMap()) }) {
            select()
        }.decodeList<ScheduleDto>()
    }

    suspend fun sharedSchedulesForAvailability(contactId: String): SharedSchedules? =
        client.postgrest.rpc("shared_schedules_for_availability", buildJsonObject {
            put("contact_id", contactId)
        }).decodeList<SharedSchedules>().singleOrNull()

    suspend fun changeScheduleOccurrence(id: String, originalDate: String, change: ScheduleException?): ScheduleDto =
        client.postgrest.rpc("change_schedule_occurrence", buildJsonObject {
            put("schedule_id", id)
            put("original_date", originalDate)
            put("action", when {
                change == null -> "restore"
                change.cancelled -> "cancel"
                else -> "move"
            })
            if (change?.date != null) {
                put("new_date", change.date)
                put("new_start", change.startTime)
                put("new_end", change.endTime)
                put("new_overnight", change.endsNextDay)
            }
        }).decodeSingle<ScheduleDto>()

    /** Own pauses and those of people who share their schedule; RLS decides. */
    suspend fun pauses() = client.postgrest["schedule_pauses"].select().decodeList<SchedulePause>()

    suspend fun addPause(title: String, start: String, end: String): SchedulePause =
        client.postgrest["schedule_pauses"].insert(buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("user_id", userId())
            put("title", title.trim())
            put("start_date", start)
            put("end_date", end)
        }) { select() }.decodeSingle<SchedulePause>()

    suspend fun deletePause(id: String) {
        client.postgrest["schedule_pauses"].delete { filter { eq("id", id); eq("user_id", userId()) } }
    }

    suspend fun freeAlerts() = client.postgrest["free_alerts"].select().decodeList<FreeAlert>()

    suspend fun addFreeAlert(targetId: String) {
        client.postgrest["free_alerts"].upsert(buildJsonObject {
            put("watcher_id", userId())
            put("target_id", targetId)
        }) { onConflict = "watcher_id,target_id"; ignoreDuplicates = true }
    }

    suspend fun removeFreeAlert(targetId: String) {
        client.postgrest["free_alerts"].delete { filter { eq("watcher_id", userId()); eq("target_id", targetId) } }
    }

    /** Plans this account sent or received; RLS returns only those. */
    suspend fun plans() = client.postgrest["plan_proposals"].select().decodeList<PlanProposal>()

    suspend fun proposePlan(recipientId: String, title: String, location: String, start: Instant, end: Instant): PlanProposal =
        client.postgrest["plan_proposals"].insert(buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("sender_id", userId())
            put("recipient_id", recipientId)
            put("title", title.trim())
            put("location", location.trim())
            put("starts_at", start.toString())
            put("ends_at", end.toString())
        }) { select() }.decodeSingle<PlanProposal>()

    suspend fun answerPlan(id: String, accept: Boolean): PlanProposal =
        client.postgrest.rpc("answer_plan", buildJsonObject { put("plan_id", id); put("accept", accept) }).decodeSingle<PlanProposal>()

    suspend fun cancelPlan(id: String): PlanProposal =
        client.postgrest.rpc("cancel_plan", buildJsonObject { put("plan_id", id) }).decodeSingle<PlanProposal>()

    suspend fun groups() = client.postgrest["person_groups"].select().decodeList<PersonGroup>()
    suspend fun groupMembers() = client.postgrest["person_group_members"].select().decodeList<PersonGroupMember>()

    /** Creates the group or renames it, then makes its members exactly [members]. */
    suspend fun saveGroup(id: String?, name: String, members: Set<String>, current: Set<String>): PersonGroup {
        val clean = name.trim()
        require(clean.length in 1..40) { "El nombre del grupo debe tener entre 1 y 40 caracteres." }
        require(members.size <= 20) { "Un grupo puede tener hasta 20 personas." }
        val group = if (id == null) client.postgrest["person_groups"].insert(buildJsonObject {
            put("id", UUID.randomUUID().toString()); put("owner_id", userId()); put("name", clean)
        }) { select() }.decodeSingle<PersonGroup>()
        else client.postgrest["person_groups"].update(buildJsonObject { put("name", clean) }) {
            filter { eq("id", id); eq("owner_id", userId()) }
            select()
        }.decodeSingle<PersonGroup>()
        val removed = current - members
        if (removed.isNotEmpty()) client.postgrest["person_group_members"].delete {
            filter { eq("group_id", group.id); isIn("member_id", removed.toList()) }
        }
        val added = members - current
        if (added.isNotEmpty()) client.postgrest["person_group_members"].insert(added.map { PersonGroupMember(group.id, it) })
        return group
    }

    suspend fun deleteGroup(id: String) {
        client.postgrest["person_groups"].delete { filter { eq("id", id); eq("owner_id", userId()) } }
    }

    /** One request, so the hourly limit either accepts the whole group message or none of it. */
    suspend fun sendSignals(recipientIds: List<String>, message: String) {
        val sender = userId()
        client.postgrest["connection_signals"].insert(recipientIds.map { recipient ->
            buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("sender_id", sender)
                put("recipient_id", recipient); put("message", message)
            }
        })
    }

    suspend fun deleteSchedule(id: String) {
        client.postgrest["personal_schedule"].delete { filter { eq("id", id); eq("user_id", userId()) } }
    }

    suspend fun setStatus(text: String, minutes: Long) {
        require(text.trim().length in 1..120)
        require(minutes in 1..1440)
        val id = userId()
        client.postgrest["personal_status"].upsert(StatusOverrideDto(
            id = id, userId = id, isActive = true, customLocation = text.trim(),
            expiresAt = Instant.now().plusSeconds(minutes * 60).toString()
        ))
    }

    suspend fun clearStatus() {
        client.postgrest["personal_status"].delete { filter { eq("user_id", userId()) } }
    }

    suspend fun createInvitation(): ConnectionInvitation {
        val invitation = ConnectionInvitation(userId(), InvitationCode.generate(),
            Instant.now().plusSeconds(86400).toString())
        client.postgrest["connection_invitations"].upsert(invitation)
        return invitation
    }

    suspend fun acceptInvitation(code: String) {
        require(InvitationCode.isValid(code)) { "Introduce el código de invitación completo, por ejemplo ABCD-EFGH." }
        val result = client.postgrest.rpc("accept_connection_invitation", buildJsonObject { put("invitation_code", InvitationCode.normalize(code)) })
        // The server returns null (not an error) for unknown or expired codes so it can count attempts.
        require(result.data.trim() != "null") { "Ese código no existe o ya venció. Pide a la otra persona uno nuevo." }
    }

    suspend fun disconnect(id: String) {
        client.postgrest["person_connections"].delete { filter { eq("id", id) } }
    }

    suspend fun quickMessages() = client.postgrest["quick_messages"].select { filter { eq("user_id", userId()) } }.decodeList<QuickMessage>()
    suspend fun saveQuickMessage(label: String, message: String) {
        require(label.trim().length in 1..30 && message.trim().length in 1..240) { "Completa el nombre y el mensaje (máximo 240 caracteres)." }
        client.postgrest["quick_messages"].insert(QuickMessage(UUID.randomUUID().toString(), userId(), label.trim(), message.trim()))
    }
    suspend fun deleteQuickMessage(id: String) {
        client.postgrest["quick_messages"].delete { filter { eq("id", id); eq("user_id", userId()) } }
    }
    suspend fun sendSignal(recipientId: String, message: String) {
        client.postgrest["connection_signals"].insert(buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("sender_id", userId())
            put("recipient_id", recipientId)
            put("message", message)
        })
    }
    suspend fun connectionPreferences() = client.postgrest["connection_preferences"].select {
        filter { eq("owner_id", userId()) }
    }.decodeList<ConnectionPreference>()

    suspend fun saveConnectionPreference(preference: ConnectionPreference) {
        val nickname = preference.nickname?.trim()?.takeIf { it.isNotEmpty() }
        require(nickname == null || nickname.length <= 40) { "El apodo puede tener hasta 40 caracteres." }
        require(preference.ownerId == userId())
        client.postgrest["connection_preferences"].upsert(preference.copy(nickname = nickname)) {
            onConflict = "connection_id,owner_id"
        }
    }

    /** The server stamps the time; only the recipient can change it. */
    suspend fun markSignalRead(id: String) {
        client.postgrest["connection_signals"].update(buildJsonObject { put("read_at", Instant.now().toString()) }) {
            filter { eq("id", id); eq("recipient_id", userId()) }
        }
    }

    suspend fun deleteSignal(id: String) {
        client.postgrest["connection_signals"].delete { filter { eq("id", id); eq("recipient_id", userId()) } }
    }

    suspend fun receivedSignals() = client.postgrest["connection_signals"].select {
        filter { eq("recipient_id", userId()); gt("created_at", Instant.now().minusSeconds(86400).toString()) }
        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        limit(20)
    }.decodeList<ConnectionSignal>()

    /** Auth answers the same way for unknown addresses, so this does not reveal who has an account. */
    suspend fun requestPasswordReset(email: String) {
        try {
            auth.resetPasswordForEmail(email.trim().lowercase(java.util.Locale.ROOT), redirectUrl = com.example.findme.domain.model.RecoveryLink.REDIRECT)
        } catch (error: io.github.jan.supabase.auth.exception.AuthRestException) {
            val limited = error.errorCode == io.github.jan.supabase.auth.exception.AuthErrorCode.OverEmailSendRateLimit ||
                error.errorCode == io.github.jan.supabase.auth.exception.AuthErrorCode.OverRequestRateLimit
            if (!limited) throw error
            throw IllegalArgumentException("Ya enviamos varios enlaces hace poco. Espera unos minutos, revisa la carpeta de spam e inténtalo de nuevo.")
        }
    }

    suspend fun exchangeRecoveryCode(code: String) {
        try { auth.exchangeCodeForSession(code) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            throw IllegalArgumentException("El enlace ya se usó, venció o se pidió desde otro teléfono. Solicita uno nuevo desde este teléfono.")
        }
    }

    suspend fun setPassword(password: String) {
        require(password.length in 8..128) { "La contraseña debe tener entre 8 y 128 caracteres." }
        auth.updateUser { this.password = password }
    }
}
