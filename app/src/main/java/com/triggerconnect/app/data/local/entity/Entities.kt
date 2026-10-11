package com.triggerconnect.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val connectionId: String,
    val userId: String,
    val displayName: String,
    val status: String,
)

@Entity(tableName = "connection_requests")
data class ConnectionRequestEntity(
    @PrimaryKey val connectionId: String,
    val fromUserId: String,
    val fromDisplayName: String,
    val createdAt: Long,
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val ownerId: String,
    val role: String,
    val memberCount: Int,
    val requiresApproval: Boolean,
)

@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"])
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val displayName: String,
    val role: String,
    val status: String,
    val joinedAt: Long,
)

@Entity(tableName = "saved_messages")
data class SavedMessageEntity(
    @PrimaryKey val messageId: String,
    val content: String,
    val createdAt: Long,
)

@Entity(tableName = "trigger_events")
data class TriggerEventEntity(
    @PrimaryKey val eventId: String,
    val senderId: String,
    val senderName: String,
    val destinationType: String,
    val destinationId: String,
    val destinationName: String,
    val content: String,
    val priority: String,
    val status: String,
    val createdAt: Long,
    val direction: String,
    val recipientCount: Int,
    val acknowledgedCount: Int,
    val myAcknowledgment: String,
)
