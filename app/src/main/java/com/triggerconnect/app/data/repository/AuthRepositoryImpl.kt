package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.data.local.SecureStorage
import com.triggerconnect.app.data.local.AppDatabase
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.AuthResponse
import com.triggerconnect.app.data.remote.dto.LoginRequest
import com.triggerconnect.app.data.remote.dto.RegisterRequest
import com.triggerconnect.app.domain.model.UserProfile
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.domain.repository.RegistrationResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthRepositoryImpl(
    private val client: SupabaseClient,
    private val api: SupabaseApi,
    private val secureStorage: SecureStorage,
    private val appScope: CoroutineScope,
    private val database: AppDatabase,
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // Keep auth state in sync with the SDK's session lifecycle (refresh
        // failures, external sign-out, auto-load from encrypted storage).
        appScope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val id = status.session.user?.id ?: secureStorage.userId
                        if (id != null) {
                            secureStorage.userId = id
                            _authState.value = AuthState.Authenticated(currentProfile(id))
                        }
                    }
                    else -> {
                        val name = status::class.simpleName.orEmpty()
                        // While the SDK is still loading/initializing, stay "Unknown"
                        // so the splash gate doesn't bounce the user to the login screen.
                        _authState.value = if (name.contains("Initial", true) ||
                            name.contains("Loading", true)
                        ) {
                            AuthState.Unknown
                        } else {
                            secureStorage.clear()
                            database.clearAllTables()
                            AuthState.Unauthenticated
                        }
                    }
                }
            }
        }
    }

    override suspend fun bootstrap() {
        // The SDK auto-loads the session from SecureSessionManager; if nothing
        // is present, reflect that immediately.
        if (!secureStorage.hasSession && client.auth.currentSessionOrNull() == null) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun register(displayName: String): AppResult<RegistrationResult> {
        val result = api.callFunction<RegisterRequest, AuthResponse>(
            name = Constants.Functions.REGISTER,
            body = RegisterRequest(displayName = displayName.trim()),
        )
        return result.map { auth ->
            establishSession(auth)
            RegistrationResult(
                profile = UserProfile(auth.userId, auth.displayName),
                loginCode = auth.loginCode.orEmpty(),
            )
        }
    }

    override suspend fun login(code: String): AppResult<UserProfile> {
        val result = api.callFunction<LoginRequest, AuthResponse>(
            name = Constants.Functions.LOGIN,
            body = LoginRequest(code = code.trim(), deviceId = secureStorage.deviceId),
        )
        return result.map { auth ->
            establishSession(auth)
            UserProfile(auth.userId, auth.displayName)
        }
    }

    /** Exchange the one-time bootstrap token for a real GoTrue session. */
    private suspend fun establishSession(auth: AuthResponse) {
        client.auth.signInWith(Email) {
            this.email = auth.authEmail
            this.password = auth.bootstrapToken
        }
        secureStorage.userId = auth.userId
        secureStorage.displayName = auth.displayName
        secureStorage.authEmail = auth.authEmail
        _authState.value = AuthState.Authenticated(UserProfile(auth.userId, auth.displayName))
    }

    override suspend fun logout() {
        runCatching { client.auth.signOut() }
        secureStorage.clear()
        database.clearAllTables()
        _authState.value = AuthState.Unauthenticated
    }

    override fun currentUserId(): String? =
        secureStorage.userId ?: client.auth.currentSessionOrNull()?.user?.id

    private fun currentProfile(id: String): UserProfile =
        UserProfile(userId = id, displayName = secureStorage.displayName ?: "You")
}
