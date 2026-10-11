package com.triggerconnect.app.di

import android.content.Context
import com.triggerconnect.app.BuildConfig
import com.triggerconnect.app.core.DefaultDispatcherProvider
import com.triggerconnect.app.core.DispatcherProvider
import com.triggerconnect.app.core.network.ConnectivityNetworkMonitor
import com.triggerconnect.app.core.network.NetworkMonitor
import com.triggerconnect.app.data.local.AppDatabase
import com.triggerconnect.app.data.local.PreferencesStore
import com.triggerconnect.app.data.local.SecureStorage
import com.triggerconnect.app.data.local.dao.ContactDao
import com.triggerconnect.app.data.local.dao.GroupDao
import com.triggerconnect.app.data.local.dao.SavedMessageDao
import com.triggerconnect.app.data.local.dao.TriggerEventDao
import com.triggerconnect.app.data.remote.SecureSessionManager
import com.triggerconnect.app.data.remote.SupabaseApi
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor =
        ConnectivityNetworkMonitor(context)

    @Provides @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage =
        SecureStorage(context)

    @Provides @Singleton
    fun providePreferencesStore(@ApplicationContext context: Context): PreferencesStore =
        PreferencesStore(context)

    @Provides @Singleton
    fun provideSupabaseClient(
        secureStorage: SecureStorage,
        json: Json,
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        defaultSerializer = KotlinXSerializer(json)
        install(Auth) {
            sessionManager = SecureSessionManager(secureStorage, json)
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        install(Realtime)
        install(Functions)
    }

    @Provides @Singleton
    fun provideSupabaseApi(client: SupabaseClient, json: Json): SupabaseApi =
        SupabaseApi(client, json)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun provideSavedMessageDao(db: AppDatabase): SavedMessageDao = db.savedMessageDao()
    @Provides fun provideTriggerEventDao(db: AppDatabase): TriggerEventDao = db.triggerEventDao()
}
