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

package com.jabook.app.jabook.compose.data.torrent

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import com.jabook.app.jabook.compose.data.network.TorrentDownloadNetworkPolicy
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.worker.WorkConstraintsPolicy
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level coordinator for torrent operations
 * Manages session lifecycle and coordinates between SessionManager and Service
 */
@Singleton
public class TorrentManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val session: TorrentSession,
        private val repository: TorrentDownloadRepository,
        private val settingsRepository: SettingsRepository,
        private val networkMonitor: NetworkMonitor,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("TorrentManager")

        /** Current downloads */
        public val downloadsFlow: StateFlow<Map<String, TorrentDownload>>
            get() = session.downloadsFlow

        @Volatile
        private var isInitialized = false
        private var scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO + loggingCoroutineExceptionHandler("ComposeTorrentManager"),
            )

        // Observation jobs are restarted on every initialize() after a shutdown(),
        // so they must be cancelled first or each service lifecycle would add duplicate collectors.
        private var dbSyncJob: Job? = null
        private var networkConstraintJob: Job? = null

        /**
         * Initialize torrent system
         */
        @Synchronized
        public fun initialize() {
            if (isInitialized) {
                logger.w { "Already initialized" }
                return
            }

            try {
                session.initSession()
                session.restoreActiveDownloads()
                isInitialized = true
                logger.i { "TorrentManager initialized" }

                // Start observing downloads for DB sync
                dbSyncJob?.cancel()
                dbSyncJob = observeAndSyncToDatabase()

                // Start observing network constraints
                networkConstraintJob?.cancel()
                networkConstraintJob = observeNetworkConstraints()
            } catch (e: NoSuchMethodError) {
                logger.e({ "libtorrent4j version mismatch - native library incompatible" }, e)
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
                isInitialized = false
            } catch (e: Exception) {
                logger.e({ "Failed to initialize" }, e)
                // Don't throw - allow app to continue
                isInitialized = false
            }
        }

        /**
         * Add torrent and start download service
         */
        public fun addTorrent(
            magnetUri: String,
            savePath: String,
            selectedFileIndices: List<Int>? = null,
            topicId: String? = null,
        ): Result<String> {
            ensureInitialized()

            val result = session.addTorrent(magnetUri, savePath, selectedFileIndices, topicId)

            if (result.isSuccess) {
                // Start foreground service
                startDownloadService()
            }

            return result
        }

        /**
         * Add torrent from magnet link (compatibility alias for addTorrent).
         * Returns the info hash on success, or throws on failure.
         *
         * @param magnetUri Magnet link to add
         * @param savePath Directory to save files
         * @param sequential Enable sequential download for streaming
         * @return Info hash of the added torrent
         */
        public fun addMagnetLink(
            magnetUri: String,
            savePath: String,
            sequential: Boolean = true,
        ): String {
            val result = addTorrent(magnetUri, savePath)
            if (result.isSuccess) {
                val hash = result.getOrThrow()
                if (sequential) {
                    enableStreaming(hash)
                }
                return hash
            } else {
                throw result.exceptionOrNull() ?: IllegalStateException("Failed to add magnet link")
            }
        }

        /**
         * Remove torrent
         */
        public fun removeTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            session.removeTorrent(hash, deleteFiles)

            // Stop service if no active downloads
            if (downloadsFlow.value.isEmpty()) {
                stopDownloadService()
            }
        }

        /**
         * Pause torrent
         */
        public fun pauseTorrent(hash: String) {
            session.pauseTorrent(hash)
        }

        /**
         * Resume torrent
         */
        public fun resumeTorrent(hash: String) {
            session.resumeTorrent(hash)
            startDownloadService()
        }

        /**
         * Stop torrent (remove from session but keep in DB)
         */
        public fun stopTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            session.removeTorrent(hash, deleteFiles)
        }

        /**
         * Pause all torrents
         */
        public fun pauseAll() {
            session.pauseAll()
        }

        /**
         * Resume all torrents
         */
        public fun resumeAll() {
            session.resumeAll()
            startDownloadService()
        }

        /**
         * Get specific download
         */
        public fun getDownload(hash: String): TorrentDownload? = session.getDownload(hash)

        /**
         * Enable streaming mode for torrent
         */
        public fun enableStreaming(hash: String) {
            session.setSequentialDownload(hash, true)
        }

        /**
         * Prioritize specific file (e.g. for streaming)
         */
        public fun prioritizeFile(
            hash: String,
            fileIndex: Int,
            priority: Int,
        ) {
            session.prioritizeFile(hash, fileIndex, priority)
        }

        /**
         * Prioritize multiple files
         */
        public fun prioritizeFiles(
            hash: String,
            priorities: List<Int>,
        ) {
            session.setFilePriorities(hash, priorities)
        }

        /**
         * Check if file is ready for streaming
         */
        public fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
        ): Boolean = session.isFileReadyForStreaming(hash, fileIndex)

        /**
         * Get downloaded bytes
         */
        public fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long = session.getDownloadedBytes(hash, fileIndex)

        /**
         * Delete all torrents
         */
        public fun deleteAllTorrents(deleteFiles: Boolean = false) {
            val hashes = downloadsFlow.value.keys.toList()
            hashes.forEach { hash ->
                session.removeTorrent(hash, deleteFiles)
            }
            stopDownloadService()
        }

        /**
         * Shutdown torrent system: persists resume data via [TorrentSession.stopSession]
         * and releases the native session. Safe to call repeatedly; a later
         * [initialize] restarts the session and its observers.
         */
        @Synchronized
        public fun shutdown() {
            try {
                // Persist final states synchronously — sampling may still hold
                // the last emission (e.g. COMPLETED/PAUSED) unsaved.
                runBlocking { repository.saveAll(downloadsFlow.value.values.toList()) }
                session.stopSession()
                dbSyncJob?.cancel()
                dbSyncJob = null
                networkConstraintJob?.cancel()
                networkConstraintJob = null
                scope.cancel()
                scope =
                    CoroutineScope(
                        SupervisorJob() + Dispatchers.IO + loggingCoroutineExceptionHandler("ComposeTorrentManager"),
                    )
                stopDownloadService()
                isInitialized = false
                logger.i { "TorrentManager shut down" }
            } catch (e: Exception) {
                logger.e({ "Error during shutdown" }, e)
            }
        }

        private fun ensureInitialized() {
            if (!isInitialized) {
                try {
                    initialize()
                    // Check if initialization actually succeeded
                    if (!isInitialized) {
                        throw IllegalStateException(
                            "TorrentManager initialization failed - libtorrent4j may not be available",
                        )
                    }
                } catch (e: Exception) {
                    logger.e({ "Failed to ensure initialization" }, e)
                    throw IllegalStateException("TorrentManager not initialized: ${e.message}", e)
                }
            }
        }

        private fun startDownloadService() {
            try {
                val intent =
                    Intent(context, TorrentDownloadService::class.java).apply {
                        action = TorrentDownloadService.ACTION_START
                    }
                androidx.core.content.ContextCompat
                    .startForegroundService(context, intent)
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                // Android 12+ blocks FGS start from background — reschedule via WorkManager
                logger.w {
                    "Cannot start FGS from background (Android 12+ restriction), " +
                        "falling back to WorkManager: ${e.message}"
                }
                scheduleServiceStartViaWorkManager()
            } catch (e: IllegalStateException) {
                logger.w { "Cannot start foreground service (may already be running): ${e.message}" }
            } catch (e: Exception) {
                logger.e({ "Failed to start download service" }, e)
            }
        }

        private fun scheduleServiceStartViaWorkManager() {
            try {
                val request =
                    OneTimeWorkRequestBuilder<TorrentStartWorker>()
                        .setConstraints(WorkConstraintsPolicy.userInitiatedDownload())
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    TorrentStartWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
                logger.i { "Scheduled TorrentStartWorker (${TorrentStartWorker.WORK_NAME}) as FGS fallback" }
            } catch (e: Exception) {
                logger.e({ "Failed to schedule TorrentStartWorker fallback" }, e)
            }
        }

        private fun stopDownloadService() {
            try {
                context.stopService(Intent(context, TorrentDownloadService::class.java))
            } catch (e: Exception) {
                logger.e({ "Failed to stop download service" }, e)
            }
        }

        /**
         * Start observing downloads and sync to database
         */
        @OptIn(FlowPreview::class)
        private fun observeAndSyncToDatabase(): Job =
            scope.launch {
                // libtorrent alerts can emit many times/sec during an active
                // transfer; persisting every emission rewrites all active rows
                // each time (write amplification). Sample to ~1 batch/sec —
                // unlike a drop-based throttle, the trailing emission (final
                // COMPLETED/PAUSED state) is always delivered.
                // Resume data is persisted separately via SAVE_RESUME_DATA.
                downloadsFlow
                    .sample(DB_SYNC_THROTTLE_MS)
                    .collect { downloads ->
                        if (downloads.isEmpty()) return@collect
                        repository.saveAll(downloads.values.toList())
                    }
            }

        private val networkPausedTorrents = mutableSetOf<String>()
        private var pausedByNetwork = false

        private fun observeNetworkConstraints(): Job =
            scope.launch {
                combine(
                    settingsRepository.userPreferences,
                    networkMonitor.networkType,
                ) { prefs: UserPreferences, net: NetworkType ->
                    Pair(prefs.wifiOnlyDownload, net)
                }.collect { (wifiOnly, net) ->
                    handleNetworkChange(wifiOnly, net)
                }
            }

        private fun handleNetworkChange(
            wifiOnly: Boolean,
            net: NetworkType,
        ) {
            val isRestricted =
                TorrentDownloadNetworkPolicy.shouldPauseForNetwork(
                    wifiOnlyEnabled = wifiOnly,
                    networkType = net,
                )

            if (isRestricted) {
                if (!pausedByNetwork) {
                    val currentDownloads = downloadsFlow.value

                    // Identify active downloads to pause
                    val active =
                        currentDownloads.values
                            .filter {
                                it.state != TorrentState.PAUSED &&
                                    it.state != TorrentState.ERROR &&
                                    it.state != TorrentState.STOPPED
                            }.map { it.hash }

                    if (active.isNotEmpty()) {
                        networkPausedTorrents.clear()
                        networkPausedTorrents.addAll(active)

                        logger.i { "Pausing ${active.size} torrents (network restricted)" }
                        active.forEach { pauseTorrent(it) }
                        pausedByNetwork = true

                        // Debounce toasts: on flappy networks the pause/resume toggles
                        // repeatedly — don't spam the user with one toast per transition.
                        showNetworkToast(context, R.string.downloadsPausedWifiRequired)
                    }
                }
            } else {
                // WiFi, Ethernet, or restriction disabled
                if (pausedByNetwork) {
                    logger.i { "Resuming ${networkPausedTorrents.size} torrents (Restored from Network pause)" }
                    networkPausedTorrents.forEach { resumeTorrent(it) }
                    networkPausedTorrents.clear()
                    pausedByNetwork = false

                    // Debounce toasts: one per transition burst, not per network flap.
                    showNetworkToast(context, R.string.downloadsResumed)
                }
            }
        }

        // Debounce helper: at most one network-restriction toast every 10s.
        private var lastNetworkToastMs = 0L

        private fun showNetworkToast(
            context: android.content.Context,
            resId: Int,
        ) {
            val now = System.currentTimeMillis()
            if (now - lastNetworkToastMs < NETWORK_TOAST_DEBOUNCE_MS) return
            lastNetworkToastMs = now
            // Toast requires a Looper thread; this is called from the
            // Dispatchers.IO collector — post to the main looper.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    Toast
                        .makeText(context, context.getString(resId), Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    logger.w({ "Failed to show network toast" }, e)
                }
            }
        }

        public companion object {
            private const val NETWORK_TOAST_DEBOUNCE_MS = 10_000L
            private const val DB_SYNC_THROTTLE_MS = 1_000L
        }
    }
