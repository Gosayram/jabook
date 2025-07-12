package com.jabook.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** JaBook Application class. Main entry point for the audiobook player application. Configured with Hilt for dependency injection. */
@HiltAndroidApp
class JaBookApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize debug logging in debug mode
        if (BuildConfig.DEBUG_MODE) {
            initializeDebugLogging()
        }
    }

    private fun initializeDebugLogging() {
        // TODO: Initialize debug logging system
        // This will be implemented in the debug module
    }
}
