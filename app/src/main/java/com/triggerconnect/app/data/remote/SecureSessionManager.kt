package com.triggerconnect.app.data.remote

import com.triggerconnect.app.data.local.SecureStorage
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Stores the Supabase [UserSession] in Android Keystore-backed encrypted
 * storage instead of the SDK's default plaintext SharedPreferences. This
 * satisfies the spec's requirement that session material be held in
 * Keystore-protected storage.
 *
 * supabase-kt drives autosave/autoload/refresh through this interface.
 */
class SecureSessionManager(
    private val secureStorage: SecureStorage,
    private val json: Json,
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        secureStorage.rawSession = json.encodeToString(UserSession.serializer(), session)
        // Keep a quick-restore copy of the identity for cold start.
        secureStorage.userId = session.user?.id ?: secureStorage.userId
        secureStorage.accessToken = session.accessToken
        secureStorage.refreshToken = session.refreshToken
    }

    override suspend fun loadSession(): UserSession? {
        val raw = secureStorage.rawSession ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        secureStorage.rawSession = null
        secureStorage.accessToken = null
        secureStorage.refreshToken = null
    }
}
