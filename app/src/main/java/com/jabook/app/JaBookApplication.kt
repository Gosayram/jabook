package com.jabook.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * JaBook Application class
 * Initializes dependency injection and sets up the application context
 */
class JaBookApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Koin dependency injection
        startKoin {
            // Android logger for Koin (only in debug builds)
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            
            // Android context
            androidContext(this@JaBookApplication)
            
            // Modules for dependency injection
            modules(
                // Core modules will be added here as they are implemented
                // listOf(
                //     coreNetModule,
                //     coreEndpointsModule,
                //     coreAuthModule,
                //     coreParseModule,
                //     coreTorrentModule,
                //     coreStreamModule,
                //     corePlayerModule,
                //     coreLoggingModule
                // )
            )
        }
    }
}