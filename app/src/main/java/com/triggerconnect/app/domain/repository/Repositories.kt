package com.triggerconnect.app.domain.repository

import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.Contact
import com.triggerconnect.app.domain.model.ConnectionRequest
import com.triggerconnect.app.domain.model.EventRecipient
import com.triggerconnect.app.domain.model.Group
import com.triggerconnect.app.domain.model.GroupInvitation
import com.triggerconnect.app.domain.model.GroupMember
import com.triggerconnect.app.domain.model.InvitePreview
import com.triggerconnect.app.domain.model.SavedMessage
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.model.TriggerPriority
import com.triggerconnect.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** The current user's personal QR payload (opaque, signed invitation token). */
    suspend fun personalQrToken(): AppResult<String>
    suspend fun updateDisplayName(name: String): AppResult<UserProfile>
    suspend fun registerDeviceToken(fcmToken: String): AppResult<Unit>
    suspend fun unregisterDeviceToken(fcmToken: String): AppResult<Unit>
    suspend fun deleteAccount(): AppResult<Unit>
}

interface ConnectionRepository {
    /** Cached, realtime-updated list of accepted contacts. */
    fun observeContacts(): Flow<List<Contact>>
    fun observeIncomingRequests(): Flow<List<ConnectionRequest>>
    suspend fun refresh(): AppResult<Unit>

    /** Send a connection request using a scanned personal invitation token. */
    suspend fun requestConnection(inviteToken: String): AppResult<Unit>
    suspend fun respondToRequest(connectionId: String, accept: Boolean): AppResult<Unit>
    suspend fun removeContact(connectionId: String): AppResult<Unit>
    suspend fun blockContact(connectionId: String): AppResult<Unit>
}

interface GroupRepository {
    fun observeGroups(): Flow<List<Group>>
    fun observeMembers(groupId: String): Flow<List<GroupMember>>
    suspend fun refresh(): AppResult<Unit>
    suspend fun refreshMembers(groupId: String): AppResult<Unit>

    suspend fun createGroup(
        name: String,
        description: String?,
        imageUrl: String?,
        requiresApproval: Boolean,
    ): AppResult<Group>
    suspend fun renameGroup(groupId: String, name: String): AppResult<Unit>
    suspend fun leaveGroup(groupId: String): AppResult<Unit>
    suspend fun deleteGroup(groupId: String): AppResult<Unit>
    suspend fun removeMember(groupId: String, userId: String): AppResult<Unit>
    suspend fun setMemberRole(groupId: String, userId: String, makeAdmin: Boolean): AppResult<Unit>
    suspend fun reviewMember(groupId: String, userId: String, approve: Boolean): AppResult<Unit>

    /** Load the group's stable, unique QR invitation. */
    suspend fun createInvitation(groupId: String): AppResult<GroupInvitation>
}

interface InvitationRepository {
    /** Resolve a scanned token (personal or group) into a preview before committing. */
    suspend fun resolve(token: String): AppResult<InvitePreview>
    suspend fun joinGroup(invitationId: String): AppResult<Group>
}

interface MessageRepository {
    fun observeSavedMessages(): Flow<List<SavedMessage>>
    suspend fun refresh(): AppResult<Unit>
    suspend fun createMessage(content: String): AppResult<SavedMessage>
    suspend fun updateMessage(messageId: String, content: String): AppResult<Unit>
    suspend fun deleteMessage(messageId: String): AppResult<Unit>
}

interface TriggerRepository {
    /**
     * Fire a trigger. [clientEventId] is a client-generated UUID used for
     * idempotency so rapid double-taps or retries never create duplicates.
     */
    suspend fun sendTrigger(
        clientEventId: String,
        destinationType: com.triggerconnect.app.domain.model.DestinationType,
        destinationId: String,
        content: String,
        priority: TriggerPriority,
        notifySelf: Boolean,
    ): AppResult<TriggerEvent>

    fun observeHistory(): Flow<List<TriggerEvent>>
    suspend fun refreshHistory(): AppResult<Unit>
    suspend fun eventDetail(eventId: String): AppResult<Pair<TriggerEvent, List<EventRecipient>>>
    suspend fun acknowledge(eventId: String): AppResult<Unit>
}
