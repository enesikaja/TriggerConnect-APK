package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.data.remote.mapThrowable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    is SessionStatus.RefreshFailure -> {
                        // Transient refresh problem (offline, server hiccup).
                        // KEEP the stored session and let the user work from
                        // cache — wiping here would log people out (and lose
                        // accounts whose six-digit code was never saved) just
                        // because the app was opened without internet.
                        // A truly revoked session surfaces as NotAuthenticated.
                        val id = secureStorage.userId
                        if (id != null) {
                            _authState.value = AuthState.Authenticated(currentProfile(id))
                        } else {
                            _authState.value = AuthState.Unauthenticated
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
                            // clearAllTables() must NOT run on the main thread
                            // (Room throws). appScope is Default, but force IO
                            // to be correct regardless of the calling thread.
                            withContext(Dispatchers.IO) { database.clearAllTables() }
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
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> try {
                val auth = result.data
                establishSession(auth)
                AppResult.Success(
                    RegistrationResult(
                        profile = UserProfile(auth.userId, auth.displayName),
                        loginCode = auth.loginCode.orEmpty(),
                    ),
                )
            } catch (e: Throwable) {
                // signInWith talks to GoTrue and THROWS on failure (flaky
                // network, token-endpoint rate limit). An uncaught throw here
                // crashed the app right after a correct code was entered.
                AppResult.Failure(mapThrowable(e))
            }
        }
    }

    override suspend fun login(code: String): AppResult<UserProfile> {
        val result = api.callFunction<LoginRequest, AuthResponse>(
            name = Constants.Functions.LOGIN,
            body = LoginRequest(code = code.trim(), deviceId = secureStorage.deviceId),
        )
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> try {
                val auth = result.data
                establishSession(auth)
                AppResult.Success(UserProfile(auth.userId, auth.displayName))
            } catch (e: Throwable) {
                // See register(): never let a session-mint failure escape as
                // an uncaught exception — surface it as a retryable error.
                AppResult.Failure(mapThrowable(e))
            }
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
        // All of this is off the main thread: signOut() hits the network and
        // clearAllTables() throws if run on the UI thread. logout() is invoked
        // from a ViewModel's main-dispatcher scope, so wrap the blocking work.
        withContext(Dispatchers.IO) {
            runCatching { client.auth.signOut() }
            secureStorage.clear()
            runCatching { database.clearAllTables() }
        }
        _authState.value = AuthState.Unauthenticated
    }

    override fun currentUserId(): String? =
        secureStorage.userId ?: client.auth.currentSessionOrNull()?.user?.id

    private fun currentProfile(id: String): UserProfile =
        UserProfile(userId = id, displayName = secureStorage.displayName ?: "You")
}
