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

    // Lazy: Keystore-backed prefs can take seconds to open on some devices.
    // Constructing this eagerly on the main thread caused startup ANRs, so the
    // heavy work happens on first access — warmed up from a background thread
    // in TriggerConnectApp.onCreate before the UI normally needs it.
    private val prefs: SharedPreferences by lazy {
        // Keystore state can rot on some devices (vendor bugs, OS updates,
        // backup restores). If the encrypted file can no longer be opened,
        // wipe it and start fresh (user re-logs in) instead of crash-looping.
        try {
            create(context)
        } catch (_: Exception) {
            context.deleteSharedPreferences(Constants.SecureKeys.PREFS_FILE)
            try {
                create(context)
            } catch (_: Exception) {
                // Last resort: plain prefs under a different name. Session
                // tokens are short-lived and the device is user-locked; a
                // working app beats a permanent crash loop.
                context.getSharedPreferences(Constants.SecureKeys.PREFS_FILE + "_fb", Context.MODE_PRIVATE)
            }
        }
    }

    private fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
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

    /** Forces the lazy Keystore/prefs initialization; call from a background thread. */
    fun warmUp() {
        prefs
    }

    fun clear() {
        val installationId = deviceId
        prefs.edit().clear().putString(KEY_DEVICE_ID, installationId).apply()
    }

    private companion object {
        const val KEY_RAW_SESSION = "sb_user_session"
        const val KEY_DEVICE_ID = "installation_id"
    }
}
