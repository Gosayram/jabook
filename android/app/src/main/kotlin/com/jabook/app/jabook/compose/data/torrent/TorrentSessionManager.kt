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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.BlockFinishedAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.StateChangedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages libtorrent4j session and torrent operations
 */
@Singleton
class TorrentSessionManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private var session: SessionManager? = null
        private val torrents = mutableMapOf<String, TorrentHandle>()
        private val topicIds = mutableMapOf<String, String>()

        private val _downloadsFlow = MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())
        val downloadsFlow: StateFlow<Map<String, TorrentDownload>> = _downloadsFlow.asStateFlow()

        private val alertListener =
            object : AlertListener {
                override fun types(): IntArray =
                    intArrayOf(
                        AlertType.ADD_TORRENT.swig(),
                        AlertType.STATE_CHANGED.swig(),
                        AlertType.TORRENT_FINISHED.swig(),
                        AlertType.TORRENT_ERROR.swig(),
                        AlertType.METADATA_RECEIVED.swig(),
                        AlertType.BLOCK_FINISHED.swig(),
                    )

                override fun alert(alert: Alert<*>) {
                    when (alert) {
                        is AddTorrentAlert -> handleAddTorrent(alert)
                        is StateChangedAlert -> handleStateChanged(alert)
                        is TorrentFinishedAlert -> handleTorrentFinished(alert)
                        is TorrentErrorAlert -> handleTorrentError(alert)
                        is MetadataReceivedAlert -> handleMetadataReceived(alert)
                        is BlockFinishedAlert -> handleBlockFinished(alert)
                    }
                }
            }

        /**
         * Initialize libtorrent session
         */
        fun initSession() {
            if (session != null) {
                Log.w(TAG, "Session already initialized")
                return
            }

            try {
                val settings =
                    SettingsPack().apply {
                        // Connection settings
                        connectionsLimit(200)
                        downloadRateLimit(0) // Unlimited by default
                        uploadRateLimit(0) // Unlimited by default

                        // DHT and other settings are enabled by default in libtorrent4j
                        // Just keeping defaults

                        // Performance settings
                        activeDownloads(4)
                        activeSeeds(4)
                        activeLimit(8)
                    }

                val params = SessionParams(settings)
                session =
                    SessionManager().apply {
                        addListener(alertListener)
                        start(params)
                    }

                Log.i(TAG, "Torrent session initialized successfully")
            } catch (e: NoSuchMethodError) {
                Log.e(TAG, "libtorrent4j version mismatch - native library incompatible", e)
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libtorrent4j native library", e)
                // Don't throw - allow app to continue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize torrent session", e)
                // Don't throw - allow app to continue
            }
        }

        /**
         * Add torrent from magnet URI
         */
        fun addTorrent(
            magnetUri: String,
            savePath: String,
            selectedFileIndices: List<Int>? = null,
            topicId: String? = null,
        ): Result<String> {
            val session =
                this.session ?: run {
                    // Try to initialize if not already done
                    try {
                        initSession()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize session for addTorrent", e)
                    }
                    return@run this.session
                } ?: return Result.failure(
                    IllegalStateException("Session not initialized - libtorrent4j may not be available"),
                )

            return try {
                // Validate magnet URI format
                if (!magnetUri.startsWith("magnet:", ignoreCase = true)) {
                    Log.e(TAG, "Invalid magnet URI format: $magnetUri")
                    return Result.failure(IllegalArgumentException("Invalid magnet URI format. Must start with 'magnet:'"))
                }

                // Parse magnet URI to get info hash
                val hash =
                    parseMagnetHash(magnetUri)
                        ?: return Result.failure(IllegalArgumentException("Invalid magnet URI: cannot parse info hash"))

                // Check if already added
                if (torrents.containsKey(hash)) {
                    Log.w(TAG, "Torrent already added: $hash")
                    return Result.failure(IllegalStateException("Torrent already added"))
                }

                // Store topicId if provided
                if (topicId != null) {
                    topicIds[hash] = topicId
                }

                // Create save directory
                val saveDir = File(savePath)
                if (!saveDir.exists()) {
                    val created = saveDir.mkdirs()
                    if (!created && !saveDir.exists()) {
                        Log.e(TAG, "Failed to create save directory: $savePath")
                        return Result.failure(IllegalStateException("Failed to create save directory: $savePath"))
                    }
                }

                // Verify directory is writable
                if (!saveDir.canWrite()) {
                    Log.e(TAG, "Save directory is not writable: $savePath")
                    return Result.failure(IllegalStateException("Save directory is not writable: $savePath"))
                }

                // Add torrent - download(String magnetUri, File saveDir, torrent_flags_t flags)
                // Using empty flags (defaults)
                // Wrap in try-catch to handle any native exceptions
                try {
                    val flags = org.libtorrent4j.swig.torrent_flags_t()
                    session.download(magnetUri, saveDir, flags)
                    Log.i(TAG, "Added torrent: $hash to $savePath")
                    Result.success(hash)
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library error while adding torrent", e)
                    Result.failure(IllegalStateException("Native library error: ${e.message}", e))
                } catch (e: NoSuchMethodError) {
                    Log.e(TAG, "Method not found error while adding torrent", e)
                    Result.failure(IllegalStateException("Library version mismatch: ${e.message}", e))
                } catch (e: RuntimeException) {
                    // libtorrent4j may throw RuntimeException for various errors
                    Log.e(TAG, "Runtime error while adding torrent", e)
                    Result.failure(IllegalStateException("Failed to add torrent: ${e.message}", e))
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state while adding torrent", e)
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid argument while adding torrent", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while adding torrent", e)
                Result.failure(e)
            }
        }

        /**
         * Remove torrent
         */
        fun removeTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            val handle = torrents.remove(hash) ?: return
            topicIds.remove(hash)

            try {
                session?.remove(handle)

                if (deleteFiles) {
                    val savePath = handle.savePath()
                    File(savePath).deleteRecursively()
                }

                updateDownloads()
                Log.i(TAG, "Removed torrent: $hash (deleteFiles=$deleteFiles)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove torrent", e)
            }
        }

        /**
         * Pause torrent
         */
        fun pauseTorrent(hash: String) {
            val handle = torrents[hash] ?: return

            try {
                handle.pause()
                updateDownloads()
                Log.i(TAG, "Paused torrent: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause torrent", e)
            }
        }

        /**
         * Resume torrent
         */
        fun resumeTorrent(hash: String) {
            val handle = torrents[hash] ?: return

            try {
                handle.resume()
                updateDownloads()
                Log.i(TAG, "Resumed torrent: $hash")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume torrent", e)
            }
        }

        /**
         * Move torrent storage to new path
         */
        fun moveTorrentStorage(
            hash: String,
            newPath: String,
        ) {
            val handle = torrents[hash] ?: return
            val newDir = File(newPath)
            if (!newDir.exists()) {
                newDir.mkdirs()
            }

            try {
                handle.moveStorage(newPath)
                Log.i(TAG, "Moving storage for $hash to $newPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move storage", e)
            }
        }

        /**
         * Enable sequential download for streaming
         */
        fun setSequentialDownload(
            hash: String,
            enabled: Boolean,
        ) {
            val handle = torrents[hash] ?: return

            try {
                // setFlags(flags, mask) - use TorrentFlags
                val flags = if (enabled) org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD else org.libtorrent4j.swig.torrent_flags_t()
                val mask = org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
                handle.setFlags(flags, mask)
                Log.i(TAG, "Set sequential download for $hash: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set sequential download", e)
            }
        }

        /**
         * Pause all torrents
         */
        fun pauseAll() {
            torrents.values.forEach { it.pause() }
            updateDownloads()
        }

        /**
         * Resume all torrents
         */
        fun resumeAll() {
            torrents.values.forEach { it.resume() }
            updateDownloads()
        }

        /**
         * Get current download info
         */
        fun getDownload(hash: String): TorrentDownload? = _downloadsFlow.value[hash]

        /**
         * Stop session and cleanup
         */
        fun stopSession() {
            try {
                torrents.clear()
                session?.stop()
                session = null
                Log.i(TAG, "Session stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping session", e)
            }
        }

        // Alert handlers

        private fun handleAddTorrent(alert: AddTorrentAlert) {
            try {
                val handle = alert.handle()
                if (!handle.isValid) {
                    Log.e(TAG, "Invalid torrent handle in ADD_TORRENT alert")
                    return
                }

                val hash = handle.infoHash().toHex()
                torrents[hash] = handle

                // Resume torrent to start downloading (required by libtorrent4j)
                // According to libtorrent4j examples, handle.resume() must be called after adding
                try {
                    handle.resume()
                    Log.d(TAG, "Torrent resumed after add: $hash")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library error resuming torrent: $hash", e)
                } catch (e: NoSuchMethodError) {
                    Log.e(TAG, "Method not found error resuming torrent: $hash", e)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Runtime error resuming torrent: $hash", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resume torrent after add: $hash", e)
                }

                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleAddTorrent", e)
            }
        }

        private fun handleStateChanged(alert: StateChangedAlert) {
            updateDownloads()
        }

        private fun handleTorrentFinished(alert: TorrentFinishedAlert) {
            updateDownloads()
        }

        private fun handleTorrentError(alert: TorrentErrorAlert) {
            val hash = alert.handle().infoHash().toHex()
            Log.e(TAG, "Torrent error for $hash: ${alert.error()}")
            updateDownloads()
        }

        private fun handleMetadataReceived(alert: MetadataReceivedAlert) {
            updateDownloads()
        }

        private fun handleBlockFinished(alert: BlockFinishedAlert) {
            // Update less frequently for performance
            if (System.currentTimeMillis() % 1000 < 100) {
                updateDownloads()
            }
        }

        // Helper methods

        private fun updateDownloads() {
            val downloads =
                torrents.mapValues { (hash, handle) ->
                    createTorrentDownload(hash, handle)
                }
            _downloadsFlow.value = downloads
        }

        private fun createTorrentDownload(
            hash: String,
            handle: TorrentHandle,
        ): TorrentDownload {
            val status = handle.status()
            val torrentInfo = handle.torrentFile()

            return TorrentDownload(
                hash = hash,
                name = status.name(),
                state = mapState(status.state()),
                progress = status.progress(),
                downloadSpeed = status.downloadRate().toLong(),
                uploadSpeed = status.uploadRate().toLong(),
                totalSize = status.totalWanted(),
                downloadedSize = status.totalWantedDone(),
                uploadedSize = status.allTimeUpload(),
                numPeers = status.numPeers(),
                numSeeds = status.numSeeds(),
                eta = calculateEta(status),
                savePath = handle.savePath(),
                files = if (torrentInfo != null) mapFiles(torrentInfo, handle) else emptyList(),
                errorMessage = null, // Error tracking not available in current libtorrent4j binding
                topicId = topicIds[hash],
            )
        }

        private fun mapState(state: TorrentStatus.State): TorrentState =
            when (state) {
                TorrentStatus.State.CHECKING_FILES -> TorrentState.CHECKING
                TorrentStatus.State.DOWNLOADING_METADATA -> TorrentState.DOWNLOADING_METADATA
                TorrentStatus.State.DOWNLOADING -> TorrentState.DOWNLOADING
                TorrentStatus.State.SEEDING -> TorrentState.SEEDING
                TorrentStatus.State.FINISHED -> TorrentState.COMPLETED
                else -> TorrentState.QUEUED
            }

        /**
         * Prioritize specific file
         */
        fun prioritizeFile(
            hash: String,
            fileIndex: Int,
            priority: Int,
        ) {
            val handle = torrents[hash] ?: return
            try {
                handle.filePriority(fileIndex, org.libtorrent4j.Priority.fromSwig(priority))
                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prioritize file", e)
            }
        }

        /**
         * Set priorities for multiple files
         */
        fun setFilePriorities(
            hash: String,
            priorities: List<Int>,
        ) {
            val handle = torrents[hash] ?: return
            val torrentInfo = handle.torrentFile() ?: return
            val numFiles = torrentInfo.numFiles()

            if (priorities.size != numFiles) {
                Log.w(TAG, "Priority list size mismatch: ${priorities.size} != $numFiles")
                return
            }

            try {
                // Priority.fromSwig expects int
                val priorityArray = priorities.map { org.libtorrent4j.Priority.fromSwig(it) }.toTypedArray()
                handle.prioritizeFiles(priorityArray)
                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set file priorities", e)
            }
        }

        private fun mapFiles(
            torrentInfo: TorrentInfo,
            handle: TorrentHandle,
        ): List<TorrentFile> {
            val fileStorage = torrentInfo.files()
            val priorities = handle.filePriorities() // Returns Priority[]
            // Use empty flags to get progress in bytes, not pieces
            val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())

            return (0 until fileStorage.numFiles()).map { index ->
                val priority =
                    if (index < priorities.size) {
                        priorities[index].swig().toInt()
                    } else {
                        4 // Default priority
                    }

                val size = fileStorage.fileSize(index)
                val downloaded = if (index < progress.size) progress[index] else 0L
                val fileProgress = if (size > 0) downloaded.toFloat() / size else 0f

                TorrentFile(
                    index = index,
                    path = fileStorage.filePath(index),
                    size = size,
                    priority = priority,
                    progress = fileProgress,
                    isSelected = priority != 0,
                )
            }
        }

        /**
         * Check if file is ready for streaming (first chunk downloaded)
         * @param bufferSize bytes to check (default 10MB)
         */
        fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
            bufferSize: Long = 10 * 1024 * 1024L, // 10MB
        ): Boolean {
            val handle = torrents[hash] ?: return false
            val torrentInfo = handle.torrentFile() ?: return false
            val fileStorage = torrentInfo.files() ?: return false

            if (fileIndex < 0 || fileIndex >= fileStorage.numFiles()) return false

            val fileSize = fileStorage.fileSize(fileIndex)
            val checkSize = minOf(fileSize, bufferSize)

            // If file is very small or fully downloaded, it's ready
            val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
            val downloadedBytes = if (fileIndex < progress.size) progress[fileIndex] else 0L

            if (downloadedBytes >= fileSize) return true

            // Check specific pieces
            // We need to map file offset to pieces
            val fileOffset = fileStorage.fileOffset(fileIndex)
            val startPiece = torrentInfo.mapFile(fileIndex, 0, 0).piece()
            // We only check the beginning of the file for "start" capability
            val endOffsetInFile = minOf(fileSize, bufferSize)
            val endPiece = torrentInfo.mapFile(fileIndex, endOffsetInFile, 0).piece()

            // Check if all pieces in range are having pieces
            for (piece in startPiece..endPiece) {
                if (!handle.havePiece(piece)) {
                    return false
                }
            }

            return true
        }

        /**
         * Get exact downloaded bytes for a file
         */
        fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long {
            val handle = torrents[hash] ?: return 0L
            val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
            return if (fileIndex < progress.size) progress[fileIndex] else 0L
        }

        private fun calculateEta(status: TorrentStatus): Long {
            val remaining = status.totalWanted() - status.totalWantedDone()
            val speed = status.downloadRate()

            return if (speed > 0 && remaining > 0) {
                remaining / speed
            } else {
                -1
            }
        }

        private fun parseMagnetHash(magnetUri: String): String? =
            try {
                // Try to parse as magnet URI
                if (magnetUri.startsWith("magnet:", ignoreCase = true)) {
                    // Support both 40-char hex and 32-char base32 hashes
                    val hexRegex = "urn:btih:([a-fA-F0-9]{40})".toRegex()
                    val base32Regex = "urn:btih:([a-zA-Z2-7]{32})".toRegex()

                    hexRegex
                        .find(magnetUri)
                        ?.groupValues
                        ?.get(1)
                        ?.lowercase()
                        ?: base32Regex
                            .find(magnetUri)
                            ?.groupValues
                            ?.get(1)
                            ?.uppercase()
                } else if (magnetUri.length == 40 && magnetUri.matches(Regex("[a-fA-F0-9]{40}"))) {
                    // Already a hex hash
                    magnetUri.lowercase()
                } else {
                    // Try to extract from any URI format
                    val anyHashRegex = "(?:urn:btih:|btih:)?([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})".toRegex(RegexOption.IGNORE_CASE)
                    anyHashRegex
                        .find(magnetUri)
                        ?.groupValues
                        ?.get(1)
                        ?.lowercase()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse magnet hash from: $magnetUri", e)
                null
            }

        companion object {
            private const val TAG = "TorrentSessionManager"
        }
    }
