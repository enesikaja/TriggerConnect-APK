package com.triggerconnect.app

import android.app.Application
import com.triggerconnect.app.core.CrashReporter
import com.triggerconnect.app.messaging.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TriggerConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.flush(this)
        NotificationChannels.ensureCreated(this)
    }
}
