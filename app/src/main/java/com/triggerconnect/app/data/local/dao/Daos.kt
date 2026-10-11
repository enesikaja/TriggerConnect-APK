package com.triggerconnect.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.triggerconnect.app.data.local.entity.ConnectionRequestEntity
import com.triggerconnect.app.data.local.entity.ContactEntity
import com.triggerconnect.app.data.local.entity.GroupEntity
import com.triggerconnect.app.data.local.entity.GroupMemberEntity
import com.triggerconnect.app.data.local.entity.SavedMessageEntity
import com.triggerconnect.app.data.local.entity.TriggerEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE status = 'ACCEPTED' ORDER BY displayName COLLATE NOCASE")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<ContactEntity>) { clear(); upsertAll(items) }

    @Query("SELECT * FROM connection_requests ORDER BY createdAt DESC")
    fun observeRequests(): Flow<List<ConnectionRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequests(items: List<ConnectionRequestEntity>)

    @Query("DELETE FROM connection_requests")
    suspend fun clearRequests()

    @Transaction
    suspend fun replaceRequests(items: List<ConnectionRequestEntity>) { clearRequests(); upsertRequests(items) }
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GroupEntity>)

    @Query("DELETE FROM groups")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<GroupEntity>) { clear(); upsertAll(items) }

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY role, displayName COLLATE NOCASE")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(items: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Transaction
    suspend fun replaceMembers(groupId: String, items: List<GroupMemberEntity>) {
        clearMembers(groupId); upsertMembers(items)
    }
}

@Dao
interface SavedMessageDao {
    @Query("SELECT * FROM saved_messages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SavedMessageEntity>)

    @Query("DELETE FROM saved_messages")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<SavedMessageEntity>) { clear(); upsertAll(items) }
}

@Dao
interface TriggerEventDao {
    @Query("SELECT * FROM trigger_events ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TriggerEventEntity>>

    @Query("SELECT * FROM trigger_events WHERE eventId = :id LIMIT 1")
    suspend fun getById(id: String): TriggerEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TriggerEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TriggerEventEntity>)

    @Query("DELETE FROM trigger_events")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<TriggerEventEntity>) { clear(); upsertAll(items) }
}
