package com.triggerconnect.app.di

import com.triggerconnect.app.data.local.SecureStorage
import com.triggerconnect.app.data.local.AppDatabase
import com.triggerconnect.app.data.local.dao.ContactDao
import com.triggerconnect.app.data.local.dao.GroupDao
import com.triggerconnect.app.data.local.dao.SavedMessageDao
import com.triggerconnect.app.data.local.dao.TriggerEventDao
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.repository.AuthRepositoryImpl
import com.triggerconnect.app.data.repository.ConnectionRepositoryImpl
import com.triggerconnect.app.data.repository.GroupRepositoryImpl
import com.triggerconnect.app.data.repository.InvitationRepositoryImpl
import com.triggerconnect.app.data.repository.MessageRepositoryImpl
import com.triggerconnect.app.data.repository.TriggerRepositoryImpl
import com.triggerconnect.app.data.repository.UserRepositoryImpl
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.ConnectionRepository
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.domain.repository.InvitationRepository
import com.triggerconnect.app.domain.repository.MessageRepository
import com.triggerconnect.app.domain.repository.TriggerRepository
import com.triggerconnect.app.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAuthRepository(
        client: SupabaseClient,
        api: SupabaseApi,
        secureStorage: SecureStorage,
        appScope: CoroutineScope,
        database: AppDatabase,
    ): AuthRepository = AuthRepositoryImpl(client, api, secureStorage, appScope, database)

    @Provides @Singleton
    fun provideUserRepository(
        client: SupabaseClient,
        api: SupabaseApi,
        secureStorage: SecureStorage,
    ): UserRepository = UserRepositoryImpl(client, api, secureStorage)

    @Provides @Singleton
    fun provideConnectionRepository(
        api: SupabaseApi,
        contactDao: ContactDao,
    ): ConnectionRepository = ConnectionRepositoryImpl(api, contactDao)

    @Provides @Singleton
    fun provideGroupRepository(
        api: SupabaseApi,
        groupDao: GroupDao,
    ): GroupRepository = GroupRepositoryImpl(api, groupDao)

    @Provides @Singleton
    fun provideInvitationRepository(
        api: SupabaseApi,
        groupRepository: GroupRepository,
    ): InvitationRepository = InvitationRepositoryImpl(api, groupRepository)

    @Provides @Singleton
    fun provideMessageRepository(
        client: SupabaseClient,
        api: SupabaseApi,
        savedMessageDao: SavedMessageDao,
        secureStorage: SecureStorage,
    ): MessageRepository = MessageRepositoryImpl(client, api, savedMessageDao, secureStorage)

    @Provides @Singleton
    fun provideTriggerRepository(
        api: SupabaseApi,
        eventDao: TriggerEventDao,
    ): TriggerRepository = TriggerRepositoryImpl(api, eventDao)
}
