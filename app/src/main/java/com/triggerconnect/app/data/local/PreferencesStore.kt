package com.triggerconnect.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "trigger_connect_prefs")

/** Non-sensitive user preferences (notification toggles, defaults). */
class PreferencesStore(private val context: Context) {

    data class Prefs(
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val defaultRecipientId: String? = null,
        val defaultGroupId: String? = null,
        val confirmUrgent: Boolean = true,
    )

    val prefs: Flow<Prefs> = context.dataStore.data.map {
        Prefs(
            soundEnabled = it[SOUND] ?: true,
            vibrationEnabled = it[VIBRATION] ?: true,
            defaultRecipientId = it[DEFAULT_RECIPIENT],
            defaultGroupId = it[DEFAULT_GROUP],
            confirmUrgent = it[CONFIRM_URGENT] ?: true,
        )
    }

    suspend fun setSound(enabled: Boolean) { context.dataStore.edit { it[SOUND] = enabled } }
    suspend fun setVibration(enabled: Boolean) { context.dataStore.edit { it[VIBRATION] = enabled } }
    suspend fun setConfirmUrgent(enabled: Boolean) { context.dataStore.edit { it[CONFIRM_URGENT] = enabled } }
    suspend fun setDefaultRecipient(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(DEFAULT_RECIPIENT) else {
                it[DEFAULT_RECIPIENT] = id
                it.remove(DEFAULT_GROUP)
            }
        }
    }
    suspend fun setDefaultGroup(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(DEFAULT_GROUP) else {
                it[DEFAULT_GROUP] = id
                it.remove(DEFAULT_RECIPIENT)
            }
        }
    }

    private companion object {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val CONFIRM_URGENT = booleanPreferencesKey("confirm_urgent")
        val DEFAULT_RECIPIENT = stringPreferencesKey("default_recipient")
        val DEFAULT_GROUP = stringPreferencesKey("default_group")
    }
}
