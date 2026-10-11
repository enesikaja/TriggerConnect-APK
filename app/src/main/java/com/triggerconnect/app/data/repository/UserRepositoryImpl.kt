package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.data.local.SecureStorage
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.PersonalQrResponse
import com.triggerconnect.app.data.remote.dto.RegisterDeviceRequest
import com.triggerconnect.app.data.remote.dto.SimpleOk
import com.triggerconnect.app.domain.model.UserProfile
import com.triggerconnect.app.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class UserRepositoryImpl(
    private val client: SupabaseClient,
    private val api: SupabaseApi,
    private val secureStorage: SecureStorage,
) : UserRepository {

    override suspend fun personalQrToken(): AppResult<String> =
        api.callFunction<PersonalQrResponse>(Constants.Functions.PERSONAL_INVITE_CREATE)
            .map { it.token }

    override suspend fun updateDisplayName(name: String): AppResult<UserProfile> {
        val uid = secureStorage.userId
            ?: return AppResult.Failure(AppError(AppErrorType.UNAUTHORIZED, "Not signed in."))
        return api.runRemote {
            client.postgrest.from("users").update(
                { set("display_name", name.trim()) },
            ) { filter { eq("id", uid) } }
            secureStorage.displayName = name.trim()
            UserProfile(uid, name.trim())
        }
    }

    override suspend fun registerDeviceToken(fcmToken: String): AppResult<Unit> =
        api.callFunction<RegisterDeviceRequest, SimpleOk>(
            name = Constants.Functions.REGISTER_DEVICE,
            body = RegisterDeviceRequest(fcmToken = fcmToken),
        ).map { }

    override suspend fun unregisterDeviceToken(fcmToken: String): AppResult<Unit> =
        api.callFunction<RegisterDeviceRequest, SimpleOk>(
            name = Constants.Functions.REGISTER_DEVICE,
            body = RegisterDeviceRequest(fcmToken = fcmToken, action = "unregister"),
        ).map { }

    override suspend fun deleteAccount(): AppResult<Unit> =
        api.callFunction<SimpleOk>(Constants.Functions.ACCOUNT_DELETE).map { }
}
