package com.triggerconnect.app.data.mapper

import com.triggerconnect.app.data.local.entity.ConnectionRequestEntity
import com.triggerconnect.app.data.local.entity.ContactEntity
import com.triggerconnect.app.data.local.entity.GroupEntity
import com.triggerconnect.app.data.local.entity.GroupMemberEntity
import com.triggerconnect.app.data.local.entity.SavedMessageEntity
import com.triggerconnect.app.data.local.entity.TriggerEventEntity
import com.triggerconnect.app.data.remote.dto.ConnectionRequestRowDto
import com.triggerconnect.app.data.remote.dto.ContactRowDto
import com.triggerconnect.app.data.remote.dto.EventRecipientRowDto
import com.triggerconnect.app.data.remote.dto.EventRowDto
import com.triggerconnect.app.data.remote.dto.GroupInvitationDto
import com.triggerconnect.app.data.remote.dto.GroupMemberRowDto
import com.triggerconnect.app.data.remote.dto.GroupRowDto
import com.triggerconnect.app.data.remote.dto.InvitePreviewDto
import com.triggerconnect.app.data.remote.dto.SavedMessageRowDto
import com.triggerconnect.app.domain.model.AcknowledgmentStatus
import com.triggerconnect.app.domain.model.ConnectionRequest
import com.triggerconnect.app.domain.model.ConnectionStatus
import com.triggerconnect.app.domain.model.Contact
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.DispatchStatus
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.EventRecipient
import com.triggerconnect.app.domain.model.EventStatus
import com.triggerconnect.app.domain.model.Group
import com.triggerconnect.app.domain.model.GroupInvitation
import com.triggerconnect.app.domain.model.GroupMember
import com.triggerconnect.app.domain.model.GroupRole
import com.triggerconnect.app.domain.model.InvitePreview
import com.triggerconnect.app.domain.model.SavedMessage
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.model.TriggerPriority
import kotlinx.datetime.Instant

/** Robust ISO-8601 -> Instant; falls back to epoch 0 rather than throwing. */
fun parseInstant(iso: String?): Instant =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.fromEpochMilliseconds(0)

fun parseEpochMillis(iso: String?): Long = parseInstant(iso).toEpochMilliseconds()

// ---------------- DTO -> Entity ----------------

fun ContactRowDto.toEntity() = ContactEntity(
    connectionId = connectionId, userId = userId, displayName = displayName, status = status,
)

fun ConnectionRequestRowDto.toEntity() = ConnectionRequestEntity(
    connectionId = connectionId,
    fromUserId = fromUserId,
    fromDisplayName = fromDisplayName,
    createdAt = parseEpochMillis(createdAt),
)

fun GroupRowDto.toEntity() = GroupEntity(
    groupId = groupId,
    name = name,
    description = description,
    imageUrl = imageUrl,
    ownerId = ownerId,
    role = role,
    memberCount = memberCount,
    requiresApproval = requiresApproval,
)

fun GroupMemberRowDto.toEntity(groupId: String) = GroupMemberEntity(
    groupId = groupId,
    userId = userId,
    displayName = displayName,
    role = role,
    status = status,
    joinedAt = parseEpochMillis(joinedAt),
)

fun SavedMessageRowDto.toEntity() = SavedMessageEntity(
    messageId = messageId, content = content, createdAt = parseEpochMillis(createdAt),
)

fun EventRowDto.toEntity() = TriggerEventEntity(
    eventId = eventId,
    senderId = senderId,
    senderName = senderName,
    destinationType = destinationType,
    destinationId = destinationId,
    destinationName = destinationName,
    content = content,
    priority = priority,
    status = status,
    createdAt = parseEpochMillis(createdAt),
    direction = direction,
    recipientCount = recipientCount,
    acknowledgedCount = acknowledgedCount,
    myAcknowledgment = myAcknowledgment,
)

// ---------------- Entity -> Domain ----------------

fun ContactEntity.toDomain() = Contact(
    connectionId = connectionId,
    userId = userId,
    displayName = displayName,
    status = ConnectionStatus.fromRaw(status),
)

fun ConnectionRequestEntity.toDomain() = ConnectionRequest(
    connectionId = connectionId,
    fromUserId = fromUserId,
    fromDisplayName = fromDisplayName,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

fun GroupEntity.toDomain() = Group(
    groupId = groupId,
    name = name,
    description = description,
    imageUrl = imageUrl,
    ownerId = ownerId,
    role = GroupRole.fromRaw(role),
    memberCount = memberCount,
    requiresApproval = requiresApproval,
)

fun GroupMemberEntity.toDomain() = GroupMember(
    userId = userId,
    displayName = displayName,
    role = GroupRole.fromRaw(role),
    pendingApproval = status == "PENDING",
    joinedAt = Instant.fromEpochMilliseconds(joinedAt),
)

fun SavedMessageEntity.toDomain() = SavedMessage(
    messageId = messageId,
    content = content,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

fun TriggerEventEntity.toDomain() = TriggerEvent(
    eventId = eventId,
    senderId = senderId,
    senderName = senderName,
    destinationType = runCatching { DestinationType.valueOf(destinationType) }.getOrDefault(DestinationType.USER),
    destinationId = destinationId,
    destinationName = destinationName,
    content = content,
    priority = TriggerPriority.fromRaw(priority),
    status = EventStatus.fromRaw(status),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    direction = runCatching { EventDirection.valueOf(direction) }.getOrDefault(EventDirection.SENT),
    recipientCount = recipientCount,
    acknowledgedCount = acknowledgedCount,
    myAcknowledgment = AcknowledgmentStatus.fromRaw(myAcknowledgment),
)

// ---------------- DTO -> Domain (direct) ----------------

fun EventRowDto.toDomain() = toEntity().toDomain()

fun InvitePreviewDto.toDomain() = InvitePreview(
    invitationId = invitationId,
    type = runCatching { DestinationType.valueOf(type) }.getOrDefault(DestinationType.USER),
    targetId = targetId,
    displayName = displayName,
    memberCount = memberCount,
    requiresApproval = requiresApproval,
    alreadyMember = alreadyMember,
)

fun GroupInvitationDto.toDomain() = GroupInvitation(
    invitationId = invitationId,
    token = token,
    expiresAt = expiresAt?.let { parseInstant(it) },
    maxUses = maxUses,
    usageCount = usageCount,
)

fun EventRecipientRowDto.toDomain() = EventRecipient(
    recipientId = recipientId,
    displayName = displayName,
    dispatchStatus = DispatchStatus.fromRaw(dispatchStatus),
    acknowledgmentStatus = AcknowledgmentStatus.fromRaw(acknowledgmentStatus),
    acknowledgedAt = acknowledgedAt?.let { parseInstant(it) },
)
