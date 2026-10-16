package com.triggerconnect.app

import android.app.Application
import com.triggerconnect.app.core.CrashReporter
import com.triggerconnect.app.data.local.SecureStorage
import com.triggerconnect.app.messaging.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TriggerConnectApp : Application() {

    @Inject lateinit var secureStorage: SecureStorage

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.flush(this)
        NotificationChannels.ensureCreated(this)
        // Open the Keystore-backed prefs off the main thread so a slow
        // Keystore (common on older/loaded Samsungs) can't ANR the launch.
        Thread { runCatching { secureStorage.warmUp() } }.start()
    }
}
