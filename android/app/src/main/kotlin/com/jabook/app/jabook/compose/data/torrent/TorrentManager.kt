// Copyright 2025 Jabook Contributors
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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level coordinator for torrent operations
 * Manages session lifecycle and coordinates between SessionManager and Service
 */
@Singleton
class TorrentManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val sessionManager: TorrentSessionManager,
        private val repository: TorrentDownloadRepository,
    ) {
        /** Current downloads */
        val downloadsFlow: StateFlow<Map<String, TorrentDownload>>
            get() = sessionManager.downloadsFlow

        private var isInitialized = false
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Initialize torrent system
         */
        fun initialize() {
            if (isInitialized) {
                Log.w(TAG, "Already initialized")
                return
            }

            try {
                sessionManager.initSession()
                isInitialized = true
                Log.i(TAG, "TorrentManager initialized")

                // Start observing downloads for DB sync
                observeAndSyncToDatabase()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize", e)
                throw e
            }
        }

        /**
         * Add torrent and start download service
         */
        fun addTorrent(
            magnetUri: String,
            savePath: String,
            selectedFileIndices: List<Int>? = null,
        ): Result<String> {
            ensureInitialized()

            val result = sessionManager.addTorrent(magnetUri, savePath, selectedFileIndices)

            if (result.isSuccess) {
                // Start foreground service
                startDownloadService()
            }

            return result
        }

        /**
         * Remove torrent
         */
        fun removeTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            sessionManager.removeTorrent(hash, deleteFiles)

            // Stop service if no active downloads
            if (downloadsFlow.value.isEmpty()) {
                stopDownloadService()
            }
        }

        /**
         * Pause torrent
         */
        fun pauseTorrent(hash: String) {
            sessionManager.pauseTorrent(hash)
        }

        /**
         * Resume torrent
         */
        fun resumeTorrent(hash: String) {
            sessionManager.resumeTorrent(hash)
        }

        /**
         * Stop torrent (remove from session but keep in DB)
         */
        fun stopTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            sessionManager.removeTorrent(hash, deleteFiles)
        }

        /**
         * Pause all torrents
         */
        fun pauseAll() {
            sessionManager.pauseAll()
        }

        /**
         * Resume all torrents
         */
        fun resumeAll() {
            sessionManager.resumeAll()
        }

        /**
         * Get specific download
         */
        fun getDownload(hash: String): TorrentDownload? = sessionManager.getDownload(hash)

        /**
         * Enable streaming mode for torrent
         */
        fun enableStreaming(hash: String) {
            sessionManager.setSequentialDownload(hash, true)
        }

        /**
         * Prioritize specific file (e.g. for streaming)
         */
        fun prioritizeFile(
            hash: String,
            fileIndex: Int,
            priority: Int,
        ) {
            sessionManager.prioritizeFile(hash, fileIndex, priority)
        }

        /**
         * Shutdown torrent system
         */
        fun shutdown() {
            try {
                sessionManager.stopSession()
                stopDownloadService()
                isInitialized = false
                Log.i(TAG, "TorrentManager shut down")
            } catch (e: Exception) {
                Log.e(TAG, "Error during shutdown", e)
            }
        }

        private fun ensureInitialized() {
            if (!isInitialized) {
                initialize()
            }
        }

        private fun startDownloadService() {
            try {
                val intent =
                    Intent(context, TorrentDownloadService::class.java).apply {
                        action = TorrentDownloadService.ACTION_START
                    }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download service", e)
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
                Log.e(TAG, "Failed to stop download service", e)
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

        companion object {
            private const val TAG = "TorrentManager"
        }
    }
