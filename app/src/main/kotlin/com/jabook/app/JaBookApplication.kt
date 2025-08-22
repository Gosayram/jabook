package com.jabook.app

import android.app.Application
import com.jabook.app.shared.debug.IDebugLogger
import com.jabook.app.shared.performance.PerformanceProfiler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** JaBook Application class. Main entry point for the audiobook player application. Configured with Hilt for dependency injection. */
@HiltAndroidApp
class JaBookApplication : Application() {
  @Inject lateinit var debugLogger: IDebugLogger

  @Inject lateinit var performanceProfiler: PerformanceProfiler

  override fun onCreate() {
    super.onCreate()

    // Initialize debug logging in debug mode
    if (BuildConfig.DEBUG) {
      initializeDebugLogging()
      // Start performance monitoring in debug builds
      performanceProfiler.startProfiling()
    }
  }

  private fun initializeDebugLogging() {
    debugLogger.logInfo("JaBookApplication initialized")
  }
}
