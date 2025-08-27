package com.jabook.app

import android.app.Application
import com.jabook.core.stream.LocalHttp

/**
 * JaBook Application class
 * Initializes core services and manages application lifecycle
 */
class JaBookApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize local HTTP server
        val localHttp = LocalHttp(this)
        
        // Initialize other core services here as needed
        // For example: logging, database, etc.
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // Cleanup resources
        // For example: stop local HTTP server
    }
}