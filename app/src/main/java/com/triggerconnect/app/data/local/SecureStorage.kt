package com.triggerconnect.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.triggerconnect.app.core.Constants
import java.util.UUID

/**
 * Android Keystore-backed encrypted storage for the session tokens and the
 * minimal profile needed to restore state on launch. The six-digit login code
 * is NEVER stored here (or anywhere on device) — only the backend holds a
 * hashed verifier.
 */
class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            Constants.SecureKeys.PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var userId: String?
        get() = prefs.getString(Constants.SecureKeys.USER_ID, null)
        set(value) = prefs.edit().putString(Constants.SecureKeys.USER_ID, value).apply()

    var displayName: String?
        get() = prefs.getString(Constants.SecureKeys.DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(Constants.SecureKeys.DISPLAY_NAME, value).apply()

    var authEmail: String?
        get() = prefs.getString(Constants.SecureKeys.AUTH_EMAIL, null)
        set(value) = prefs.edit().putString(Constants.SecureKeys.AUTH_EMAIL, value).apply()

    var accessToken: String?
        get() = prefs.getString(Constants.SecureKeys.SESSION_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.SecureKeys.SESSION_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(Constants.SecureKeys.SESSION_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.SecureKeys.SESSION_REFRESH_TOKEN, value).apply()

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    /** Full serialized Supabase UserSession JSON, written by SecureSessionManager. */
    var rawSession: String?
        get() = prefs.getString(KEY_RAW_SESSION, null)
        set(value) = prefs.edit().putString(KEY_RAW_SESSION, value).apply()

    val hasSession: Boolean get() = !rawSession.isNullOrBlank() && !userId.isNullOrBlank()

    fun clear() {
        val installationId = deviceId
        prefs.edit().clear().putString(KEY_DEVICE_ID, installationId).apply()
    }

    private companion object {
        const val KEY_RAW_SESSION = "sb_user_session"
        const val KEY_DEVICE_ID = "installation_id"
    }
}
