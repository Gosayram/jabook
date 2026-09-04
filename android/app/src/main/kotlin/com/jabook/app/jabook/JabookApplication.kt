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
import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.compose.core.util.EmbeddedArtworkFetcher
import com.jabook.app.jabook.compose.data.local.JABOOK_DB_VERSION
import com.jabook.app.jabook.compose.data.sync.SyncManager
import com.jabook.app.jabook.compose.data.torrent.TorrentMemoryPressureGuard
import com.jabook.app.jabook.compose.infrastructure.notification.NotificationHelper
import com.jabook.app.jabook.crash.AnrWatchdog
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import javax.inject.Inject

/**
 * Application class for Jabook with Dagger Hilt support.
 *
 * This class initializes Dagger Hilt for dependency injection
 * and creates notification channels.
 */

@HiltAndroidApp
public class JabookApplication :
    Application(),
    androidx.work.Configuration.Provider {
    @Inject
    public lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    public lateinit var syncManager: SyncManager

    /** Cookie-free client for cover downloads — Coil must not send session cookies to cover hosts. */
    @Inject
    @javax.inject.Named("coverDownload")
    public lateinit var coverDownloadClient: OkHttpClient

    /** Lazily guards the native torrent session only when Android reports memory pressure. */
    @Inject
    public lateinit var torrentMemoryPressureGuard: TorrentMemoryPressureGuard

    /** Recovers stale listening sessions left open by a previous process death. */
    @Inject
    public lateinit var listeningSessionRepository: ListeningSessionRepository

    /** ANR watchdog — active only in debug/beta builds via LogUtils gating. */
    private val anrWatchdog: AnrWatchdog = AnrWatchdog()

    private val criticalMemoryTrimHandler =
        CriticalMemoryTrimHandler {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }

    public override val workManagerConfiguration: androidx.work.Configuration
        get() =
            androidx.work.Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    public override fun onCreate() {
        super.onCreate()

        // :crash process must not build the Hilt graph / OkHttp / DataStore (a second
        // DataStore here would lock the cookies file against the main process).
        if (android.app.Application.getProcessName() == ":crash") return

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

        // Start ANR watchdog for debug/beta builds only (BP-6.3)
        if (BuildConfig.DEBUG || BuildConfig.FLAVOR != "prod") {
            try {
                // Watchdog failure must never break app startup — it's diagnostics only.
                anrWatchdog.start()
            } catch (e: Exception) {
                LogUtils.e("JabookApplication", "Failed to start ANR watchdog", e)
            }
        }

        // Initialize Global Exception Handler (writes crash report to disk)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            com.jabook.app.jabook.crash
                .GlobalExceptionHandler(this, defaultHandler),
        )

        // Check for crash report from previous session and show CrashActivity if found
        checkAndShowCrashReport()

        // Check if previous session crashed (no clean shutdown)
        if (!com.jabook.app.jabook.crash.GlobalExceptionHandler
                .wasCleanShutdown(this)
        ) {
            LogUtils.w("JabookApplication", "Previous session did not shut down cleanly")
            maybeEnterSafeMode()
        }

        recoverOpenListeningSessions()

        // Audio offload remains disabled after a crash loop until app data is reset.
        val safeModePrefs = getSharedPreferences("jabook_crash_handler", MODE_PRIVATE)
        if (safeModePrefs.getBoolean("safe_mode", false)) {
            LogUtils.i("JabookApplication", "Running in safe mode — audio offload disabled")
        }

        // Create notification channels for downloads and player
        NotificationHelper.createNotificationChannels(this)

        // Schedule periodic sync
        syncManager.schedulePeriodicSync()

        // Schedule periodic indexing via WorkManager (daily incremental, Wi-Fi only)
        schedulePeriodicIndexing()

        // Service starts lazily on first Play via MediaController connection.
        // Eager warmup removed: Android 15+ bans media-FGS from auto-start,
        // and race condition with MediaController required up to 15s retry.

        // Configure Coil ImageLoader with OkHttpClient from Hilt
        // Use setSafe to ensure it won't overwrite an existing ImageLoader
        SingletonImageLoader.setSafe { context ->
            ImageLoader
                .Builder(context)
                .components {
                    // Use the cookie-free coverDownload client (DoH, browser headers, Brotli):
                    // covers go to arbitrary hosts, so session cookies/AuthInterceptor must not apply.
                    // Coil has its own 50MB disk cache below — no HTTP cache duplication.
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { coverDownloadClient },
                            concurrentRequestStrategy = { coil3.network.DeDupeConcurrentRequestStrategy() },
                        ),
                    )
                    // Decodes artwork embedded in local audio files (audio-artwork://<abs-path>).
                    add(EmbeddedArtworkFetcher.Factory)
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
                .apply {
                    if (BuildConfig.DEBUG) {
                        logger(coil3.util.DebugLogger())
                    }
                }.build()
        }

        LogUtils.d("JabookApplication", "Application created with Hilt support")
    }

    public override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        try {
            criticalMemoryTrimHandler.onTrimMemory(level)
        } catch (e: Exception) {
            LogUtils.e("JabookApplication", "Failed to clear heap caches during memory trim", e)
        }

        try {
            torrentMemoryPressureGuard.onTrimMemory(level)
        } catch (e: Exception) {
            // Memory trimming must never turn a native-library failure into a process crash.
            LogUtils.e("JabookApplication", "Failed to guard torrent session during memory trim", e)
        }
    }

    private fun recoverOpenListeningSessions() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                listeningSessionRepository.recoverOpenSessions()
            }.onSuccess { closedSessions ->
                if (closedSessions > 0) {
                    LogUtils.w("JabookApplication", "Closed $closedSessions stale listening sessions")
                }
            }.onFailure { error ->
                LogUtils.e("JabookApplication", "Failed to recover stale listening sessions", error)
            }
        }
    }

    private fun checkAndShowCrashReport() {
        // Disk read (crash report file) must stay off the main thread
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("jabook_crash_handler", MODE_PRIVATE)
                if (!prefs.getBoolean("has_crash_report", false)) return@launch

                val file = java.io.File(filesDir, "last_crash_report.txt")
                if (!file.exists()) {
                    prefs.edit().remove("has_crash_report").apply()
                    return@launch
                }

                val report =
                    try {
                        file.readText()
                    } catch (_: Exception) {
                        return@launch
                    }

                LogUtils.w("JabookApplication", "Found crash report from previous session, launching CrashActivity")
                val intent =
                    android.content.Intent(this@JabookApplication, com.jabook.app.jabook.crash.CrashActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra(com.jabook.app.jabook.crash.CrashActivity.EXTRA_STACK_TRACE, report)
                    }
                startActivity(intent)
            } catch (e: Exception) {
                LogUtils.e("JabookApplication", "Failed to check crash report", e)
            }
        }
    }

    private fun configureDiagnostics() {
        CrashDiagnostics.configureRuntimeContext(
            buildType = BuildConfig.BUILD_TYPE,
            flavor = BuildConfig.FLAVOR,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
        )
        CrashDiagnostics.setCustomKey("db_schema_version", JABOOK_DB_VERSION.toString())
    }

    /**
     * Enter safe mode if crash-loop detected (≥3 crashes in 60s).
     * In safe mode: disable audio offload.
     */
    private fun maybeEnterSafeMode() {
        val prefs = getSharedPreferences("jabook_crash_handler", MODE_PRIVATE)
        val crashCount = prefs.getInt("crash_count", 0)
        if (crashCount >= 3) {
            LogUtils.w("JabookApplication", "Crash-loop detected ($crashCount crashes), entering safe mode")
            prefs.edit().putBoolean("safe_mode", true).apply()
        }
    }

    /**
     * Schedule periodic forum indexing via WorkManager.
     * Runs daily on Wi-Fi, respects charging/idle constraints.
     */
    private fun schedulePeriodicIndexing() {
        try {
            val constraints =
                androidx.work.Constraints
                    .Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val workRequest =
                androidx.work
                    .PeriodicWorkRequestBuilder<
                        com.jabook.app.jabook.compose.data.worker.IndexingWorker,
                    >(24, java.util.concurrent.TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInitialDelay(6, java.util.concurrent.TimeUnit.HOURS)
                    .addTag("periodic_indexing")
                    .build()

            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                com.jabook.app.jabook.compose.data.worker.IndexingWorker.WORK_NAME_PERIODIC,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
            LogUtils.d("JabookApplication", "Periodic indexing scheduled (daily, Wi-Fi only)")
        } catch (e: Exception) {
            LogUtils.e("JabookApplication", "Failed to schedule periodic indexing", e)
        }
    }
}
