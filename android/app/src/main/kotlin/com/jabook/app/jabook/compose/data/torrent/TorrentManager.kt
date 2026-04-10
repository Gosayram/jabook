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
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

        private var isInitialized = false
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO + loggingCoroutineExceptionHandler("ComposeTorrentManager"),
            )

        /**
         * Initialize torrent system
         */
        public fun initialize() {
            if (isInitialized) {
                logger.w { "Already initialized" }
                return
            }

            try {
                session.initSession()
                isInitialized = true
                logger.i { "TorrentManager initialized" }

                // Start observing downloads for DB sync
                observeAndSyncToDatabase()

                // Start observing network constraints
                observeNetworkConstraints()
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
         * Move torrent to new path
         */
        public fun moveTorrent(
            hash: String,
            newPath: String,
        ) {
            session.moveTorrentStorage(hash, newPath)
        }

        /**
         * Resume all torrents
         */
        public fun resumeAll() {
            session.resumeAll()
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
         * Shutdown torrent system
         */
        public fun shutdown() {
            try {
                session.stopSession()
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
                // Use ContextCompat for better compatibility
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    androidx.core.content.ContextCompat
                        .startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // Service might already be running or context is invalid
                logger.w { "Cannot start foreground service (may already be running): ${e.message}" }
            } catch (e: Exception) {
                logger.e({ "Failed to start download service" }, e)
            }
        }

        private fun stopDownloadService() {
            try {
                val intent =
                    Intent(context, TorrentDownloadService::class.java).apply {
                        action = TorrentDownloadService.ACTION_STOP
                    }
                context.startService(intent)
            } catch (e: Exception) {
                logger.e({ "Failed to stop download service" }, e)
            }
        }

        /**
         * Start observing downloads and sync to database
         */
        private fun observeAndSyncToDatabase() {
            scope.launch {
                downloadsFlow.collect { downloads ->
                    if (downloads.isNotEmpty()) {
                        repository.saveAll(downloads.values.toList())
                    }
                }
            }
        }

        private val networkPausedTorrents = mutableSetOf<String>()
        private var pausedByNetwork = false

        private fun observeNetworkConstraints() {
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
        }

        private fun handleNetworkChange(
            wifiOnly: Boolean,
            net: NetworkType,
        ) {
            val isRestricted = wifiOnly && net == NetworkType.CELLULAR

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

                        logger.i { "Pausing ${active.size} torrents due to WiFi-only restriction" }
                        active.forEach { pauseTorrent(it) }
                        pausedByNetwork = true

                        // Show notification about paused downloads
                        android.widget.Toast
                            .makeText(
                                context,
                                "Downloads paused (WiFi required)",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            } else {
                // WiFi, Ethernet, or restriction disabled
                if (pausedByNetwork) {
                    logger.i { "Resuming ${networkPausedTorrents.size} torrents (Restored from Network pause)" }
                    networkPausedTorrents.forEach { resumeTorrent(it) }
                    networkPausedTorrents.clear()
                    pausedByNetwork = false

                    // Show notification about resumed downloads
                    android.widget.Toast
                        .makeText(
                            context,
                            "Downloads resumed",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
