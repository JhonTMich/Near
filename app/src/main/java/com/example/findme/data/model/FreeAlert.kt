package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A pending "tell me when they are free" request. Only the person who asked can read it. */
@Serializable
data class FreeAlert(
    @SerialName("watcher_id") val watcherId: String,
    @SerialName("target_id") val targetId: String,
    @SerialName("expires_at") val expiresAt: String
)
