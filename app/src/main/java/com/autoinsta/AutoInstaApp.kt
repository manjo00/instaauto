package com.autoinsta

import android.app.Application

/**
 * Application entry point. Phase 0: nothing to wire yet.
 * Later phases initialize Room, WorkManager config, and the notification channel here.
 */
class AutoInstaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO(Phase 1): init Room database
        // TODO(Phase 3): create notification channel + WorkManager config
    }
}
