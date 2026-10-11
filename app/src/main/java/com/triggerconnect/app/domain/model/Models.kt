package com.triggerconnect.app.domain.model

import kotlinx.datetime.Instant

/** The signed-in user's own profile. */
data class UserProfile(
    val userId: String,
    val displayName: String,
    val createdAt: Instant? = null,
)

/** An accepted one-to-one contact. */
data class Contact(
    val connectionId: String,
    val userId: String,
    val displayName: String,
    val status: ConnectionStatus,
)

/** An incoming connection request awaiting this user's decision. */
data class ConnectionRequest(
    val connectionId: String,
    val fromUserId: String,
    val fromDisplayName: String,
    val createdAt: Instant,
)

/** A group as seen by the current user (role = this user's role in it). */
data class Group(
    val groupId: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val ownerId: String,
    val role: GroupRole,
    val memberCount: Int,
    val requiresApproval: Boolean = false,
)

/** A member row within a group. */
data class GroupMember(
    val userId: String,
    val displayName: String,
    val role: GroupRole,
    val pendingApproval: Boolean,
    val joinedAt: Instant,
)

/** A reusable, user-authored trigger message. */
data class SavedMessage(
    val messageId: String,
    val content: String,
    val createdAt: Instant,
)

/**
 * A unified selectable target for the TRIGGER button — either a single
 * contact or a whole group.
 */
sealed interface Recipient {
    val id: String
    val displayName: String

    data class Person(override val id: String, override val displayName: String) : Recipient
    data class GroupTarget(
        override val id: String,
        override val displayName: String,
        val memberCount: Int,
    ) : Recipient

    val destinationType: DestinationType
        get() = when (this) {
            is Person -> DestinationType.USER
            is GroupTarget -> DestinationType.GROUP
        }
}

/** A trigger event in history / details. */
data class TriggerEvent(
    val eventId: String,
    val senderId: String,
    val senderName: String,
    val destinationType: DestinationType,
    val destinationId: String,
    val destinationName: String,
    val content: String,
    val priority: TriggerPriority,
    val status: EventStatus,
    val createdAt: Instant,
    val direction: EventDirection,
    val recipientCount: Int = 0,
    val acknowledgedCount: Int = 0,
    val myAcknowledgment: AcknowledgmentStatus = AcknowledgmentStatus.NONE,
)

/** Per-recipient delivery/ack row shown to the sender in event details. */
data class EventRecipient(
    val recipientId: String,
    val displayName: String,
    val dispatchStatus: DispatchStatus,
    val acknowledgmentStatus: AcknowledgmentStatus,
    val acknowledgedAt: Instant?,
)

/** Result of resolving a scanned QR invitation token, before the user commits. */
data class InvitePreview(
    val invitationId: String,
    val type: DestinationType,
    val targetId: String,
    val displayName: String,
    val memberCount: Int?,
    val requiresApproval: Boolean,
    val alreadyMember: Boolean,
)

/** A generated group invitation (QR payload + metadata) returned to an admin. */
data class GroupInvitation(
    val invitationId: String,
    val token: String,
    val expiresAt: Instant?,
    val maxUses: Int?,
    val usageCount: Int,
)
