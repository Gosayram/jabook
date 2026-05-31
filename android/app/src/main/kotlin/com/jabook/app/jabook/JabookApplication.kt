// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook

import android.app.Application
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jabook.app.jabook.compose.data.sync.SyncManager
import com.jabook.app.jabook.compose.infrastructure.notification.NotificationHelper
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.diagnostics.AnrWatchdog
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import javax.inject.Inject

/**
 * Application class for Jabook with Dagger Hilt support.
 *
 * This class initializes Dagger Hilt for dependency injection
 * and creates notification channels.
 */

/**
 * EntryPoint to access OkHttpClient from Hilt in Application.onCreate().
 * This is needed because Hilt injection is not available in Application.onCreate().
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
public interface OkHttpClientEntryPoint {
    public fun okHttpClient(): OkHttpClient
}

@HiltAndroidApp
public class JabookApplication :
    Application(),
    androidx.work.Configuration.Provider {
    @Inject
    public lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    public lateinit var syncManager: SyncManager

    /** ANR watchdog — active only in debug/beta builds via LogUtils gating. */
    private val anrWatchdog: AnrWatchdog = AnrWatchdog()

    public override val workManagerConfiguration: androidx.work.Configuration
        get() =
            androidx.work.Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    public override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectCleartextNetwork()
                    .penaltyLog()
                    .build(),
            )
        }

        configureDiagnostics()

        // Start ANR watchdog for debug/beta builds (BP-6.3)
        anrWatchdog.start()

        // Initialize Global Exception Handler (writes crash report to disk)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            com.jabook.app.jabook.crash
                .GlobalExceptionHandler(this, defaultHandler),
        )

        // Check for crash report from previous session and show CrashActivity if found
        checkAndShowCrashReport()

        // Create notification channels for downloads and player
        NotificationHelper.createNotificationChannels(this)

        // Schedule periodic sync
        syncManager.schedulePeriodicSync()

        // Service starts lazily on first Play via MediaController connection.
        // Eager warmup removed: Android 15+ bans media-FGS from auto-start,
        // and race condition with MediaController required up to 15s retry.

        // Configure Coil ImageLoader with OkHttpClient from Hilt
        // Use setSafe to ensure it won't overwrite an existing ImageLoader
        // Note: setSafe uses lazy initialization, so Hilt will be ready when ImageLoader is first used
        SingletonImageLoader.setSafe { context ->
            // Get OkHttpClient from Hilt using EntryPoint (lazy - Hilt will be ready when first used)
            val okHttpClient =
                EntryPointAccessors
                    .fromApplication(
                        context,
                        OkHttpClientEntryPoint::class.java,
                    ).okHttpClient()

            ImageLoader
                .Builder(context)
                .components {
                    // Use the same OkHttpClient that's used for API calls
                    // This ensures images benefit from cookie persistence, auth, Brotli, etc.
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { okHttpClient },
                        ),
                    )
                }.memoryCache {
                    MemoryCache
                        .Builder()
                        // Set the max size to 25% of the app's available memory
                        .maxSizePercent(context, percent = 0.25)
                        .build()
                }.diskCache {
                    val cacheDir = context.cacheDir.resolve("image_cache")
                    cacheDir.mkdirs() // Ensure directory exists
                    DiskCache
                        .Builder()
                        .directory(cacheDir.absolutePath.toPath())
                        // Fixed 50MB limit for predictable disk usage (covers are important for UX)
                        // Fixed size prevents unbounded growth on devices with large storage
                        .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                        .build()
                }
                // Show a short crossfade when loading images asynchronously
                .crossfade(true)
                .build()
        }

        LogUtils.d("JabookApplication", "Application created with Hilt support")
    }

    private fun checkAndShowCrashReport() {
        try {
            val prefs = getSharedPreferences("jabook_crash_handler", MODE_PRIVATE)
            if (!prefs.getBoolean("has_crash_report", false)) return

            val file = java.io.File(filesDir, "last_crash_report.txt")
            if (!file.exists()) {
                prefs.edit().remove("has_crash_report").apply()
                return
            }

            val report = file.readText()
            file.delete()
            prefs.edit().remove("has_crash_report").apply()

            LogUtils.w("JabookApplication", "Found crash report from previous session, launching CrashActivity")
            val intent = android.content.Intent(this, com.jabook.app.jabook.crash.CrashActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(com.jabook.app.jabook.crash.CrashActivity.EXTRA_STACK_TRACE, report)
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogUtils.e("JabookApplication", "Failed to check crash report", e)
        }
    }

    private fun configureDiagnostics() {
        CrashDiagnostics.configureRuntimeContext(
            buildType = BuildConfig.BUILD_TYPE,
            flavor = BuildConfig.FLAVOR,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
        )
    }
}
