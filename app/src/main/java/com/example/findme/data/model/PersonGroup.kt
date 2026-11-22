package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A private group of connected people. Only its owner sees it; members are not told. */
@Serializable
data class PersonGroup(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String
)

@Serializable
data class PersonGroupMember(
    @SerialName("group_id") val groupId: String,
    @SerialName("member_id") val memberId: String
)
