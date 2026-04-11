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

        configureDiagnostics()

        // Start ANR watchdog for debug/beta builds (BP-6.3)
        anrWatchdog.start()

        // Initialize Global Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            com.jabook.app.jabook.crash
                .GlobalExceptionHandler(this, defaultHandler),
        )

        // Create notification channels for downloads and player
        NotificationHelper.createNotificationChannels(this)

        // Schedule periodic sync
        syncManager.schedulePeriodicSync()

        // CRITICAL: Start AudioPlayerService for warmup
        // This ensures instant playback readiness without delay on first play
        // Service will initialize MediaSession, ExoPlayer, and notification provider
        try {
            android.util.Log.i("JabookApplication", "Starting AudioPlayerService warmup...")
            CrashDiagnostics.log("audio_service_warmup_started")
            val serviceIntent = android.content.Intent(this, com.jabook.app.jabook.audio.AudioPlayerService::class.java)
            startService(serviceIntent)
            android.util.Log.i("JabookApplication", "AudioPlayerService warmup initiated")
            CrashDiagnostics.log("audio_service_warmup_initiated")
        } catch (e: Exception) {
            android.util.Log.e("JabookApplication", "Failed to start AudioPlayerService warmup", e)
            CrashDiagnostics.reportNonFatal(
                tag = "audio_service_warmup_failed",
                throwable = e,
                attributes = mapOf("phase" to "application_on_create"),
            )
        }

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
                        // Increased to 5% for better cover caching - covers are important for UX
                        .maxSizePercent(0.05)
                        .build()
                }
                // Show a short crossfade when loading images asynchronously
                .crossfade(true)
                .build()
        }

        android.util.Log.d("JabookApplication", "Application created with Hilt support")
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
