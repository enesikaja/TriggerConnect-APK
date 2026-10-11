package com.triggerconnect.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==========================================================================
//  Edge Function request bodies (client -> server). camelCase; we own both ends.
// ==========================================================================

@Serializable data class RegisterRequest(val displayName: String)
@Serializable data class LoginRequest(val code: String, val deviceId: String)
@Serializable data class RegisterDeviceRequest(
    val fcmToken: String,
    val platform: String = "android",
    val action: String = "register",
)
@Serializable data class ConnectionRequestBody(val inviteToken: String)
@Serializable data class ConnectionRespondBody(
    val connectionId: String,
    val action: String,
    val accept: Boolean? = null,
)
@Serializable data class GroupInviteCreateBody(val groupId: String)
@Serializable data class InviteResolveBody(val token: String)
@Serializable data class GroupJoinBody(val invitationId: String)
@Serializable data class EventAcknowledgeBody(val eventId: String)

@Serializable
data class TriggerDispatchBody(
    val clientEventId: String,
    val destinationType: String,
    val destinationId: String,
    val content: String,
    val priority: String,
    val notifySelf: Boolean,
)

@Serializable
data class GroupManageBody(
    val action: String, // create | rename | leave | delete | removeMember | setRole | revokeInvites
    val groupId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val requiresApproval: Boolean? = null,
    val targetUserId: String? = null,
    val makeAdmin: Boolean? = null,
    val approve: Boolean? = null,
)

// ==========================================================================
//  Edge Function responses (server -> client).
// ==========================================================================

@Serializable
data class AuthResponse(
    val userId: String,
    val displayName: String,
    val authEmail: String,
    val bootstrapToken: String,
    val loginCode: String? = null, // present only on register
)

@Serializable
data class InvitePreviewDto(
    val invitationId: String,
    val type: String,
    val targetId: String,
    val displayName: String,
    val memberCount: Int? = null,
    val requiresApproval: Boolean = false,
    val alreadyMember: Boolean = false,
)

@Serializable
data class GroupInvitationDto(
    val invitationId: String,
    val token: String,
    val expiresAt: String? = null,
    val maxUses: Int? = null,
    val usageCount: Int = 0,
)

@Serializable
data class GroupJoinResponse(
    val group: GroupRowDto,
    val joined: Boolean,
    val pendingApproval: Boolean = false,
)

@Serializable
data class TriggerDispatchResponse(
    val event: EventRowDto,
    val recipientCount: Int,
)

@Serializable data class SimpleOk(val ok: Boolean = true)
@Serializable data class PersonalQrResponse(val token: String)

@Serializable
data class GroupManageResponse(
    val ok: Boolean = true,
    val group: GroupRowDto? = null,
)

// ==========================================================================
//  RPC row DTOs (reads via Postgres functions). snake_case from the DB.
// ==========================================================================

@Serializable
data class ContactRowDto(
    @SerialName("connection_id") val connectionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val status: String,
)

@Serializable
data class ConnectionRequestRowDto(
    @SerialName("connection_id") val connectionId: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("from_display_name") val fromDisplayName: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class GroupRowDto(
    @SerialName("group_id") val groupId: String,
    val name: String,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("owner_id") val ownerId: String,
    val role: String = "MEMBER",
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
)

@Serializable
data class GroupMemberRowDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    val status: String = "ACTIVE",
    @SerialName("joined_at") val joinedAt: String,
)

@Serializable
data class SavedMessageRowDto(
    @SerialName("message_id") val messageId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class EventRowDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("destination_type") val destinationType: String,
    @SerialName("destination_id") val destinationId: String,
    @SerialName("destination_name") val destinationName: String,
    val content: String,
    val priority: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    val direction: String = "SENT",
    @SerialName("recipient_count") val recipientCount: Int = 0,
    @SerialName("acknowledged_count") val acknowledgedCount: Int = 0,
    @SerialName("my_acknowledgment") val myAcknowledgment: String = "NONE",
)

@Serializable
data class EventRecipientRowDto(
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("dispatch_status") val dispatchStatus: String,
    @SerialName("acknowledgment_status") val acknowledgmentStatus: String,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
)

// For saved-message direct postgrest insert/update (owner-enforced by RLS).
@Serializable
data class SavedMessageInsert(
    @SerialName("owner_id") val ownerId: String,
    val content: String,
)

@Serializable
data class SavedMessageUpdate(val content: String)
