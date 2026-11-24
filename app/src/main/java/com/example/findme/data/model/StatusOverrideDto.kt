package com.example.findme.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A person's shared status, or their current place ([kind] = [PLACE]) read from `current_place`. Both
 * travel together so every screen gets them from the same list; only a status replaces the schedule.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StatusOverrideDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("custom_location") val customLocation: String,
    @SerialName("custom_message") val customMessage: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String? = null,
    /** Never sent to `personal_status`, which has no such column; kept in the local cache for places. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val kind: String = STATUS
) {
    val isPlace: Boolean get() = kind == PLACE

    companion object {
        const val STATUS = "status"
        const val PLACE = "place"
    }
}

/** A row of `current_place`: where the person is now, from their saved places. */
@Serializable
data class CurrentPlaceDto(
    @SerialName("user_id") val userId: String,
    val label: String,
    @SerialName("expires_at") val expiresAt: String
) {
    fun asStatus() = StatusOverrideDto(id = userId, userId = userId, isActive = true, customLocation = label,
        expiresAt = expiresAt, kind = StatusOverrideDto.PLACE)
}
