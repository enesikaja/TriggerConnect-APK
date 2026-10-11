package com.triggerconnect.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.triggerconnect.app.data.local.dao.ContactDao
import com.triggerconnect.app.data.local.dao.GroupDao
import com.triggerconnect.app.data.local.dao.SavedMessageDao
import com.triggerconnect.app.data.local.dao.TriggerEventDao
import com.triggerconnect.app.data.local.entity.ConnectionRequestEntity
import com.triggerconnect.app.data.local.entity.ContactEntity
import com.triggerconnect.app.data.local.entity.GroupEntity
import com.triggerconnect.app.data.local.entity.GroupMemberEntity
import com.triggerconnect.app.data.local.entity.SavedMessageEntity
import com.triggerconnect.app.data.local.entity.TriggerEventEntity

@Database(
    entities = [
        ContactEntity::class,
        ConnectionRequestEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        SavedMessageEntity::class,
        TriggerEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun groupDao(): GroupDao
    abstract fun savedMessageDao(): SavedMessageDao
    abstract fun triggerEventDao(): TriggerEventDao

    companion object {
        const val NAME = "trigger_connect.db"
    }
}
