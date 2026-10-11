package com.triggerconnect.app.domain.repository

import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

/** Observable authentication state for navigation gating. */
sealed interface AuthState {
    data object Unknown : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val profile: UserProfile) : AuthState
}

/** Returned exactly once after registration — the six-digit code is shown to
 *  the user then never retrievable again. */
data class RegistrationResult(
    val profile: UserProfile,
    val loginCode: String,
)

interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Restore any persisted session on app start. */
    suspend fun bootstrap()

    suspend fun register(displayName: String): AppResult<RegistrationResult>

    suspend fun login(code: String): AppResult<UserProfile>

    suspend fun logout()

    fun currentUserId(): String?
}
