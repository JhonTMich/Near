package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleFollowRequest(
    val id: String,
    @SerialName("follower_id") val followerId: String,
    @SerialName("leader_id") val leaderId: String,
    val approved: Boolean
)
