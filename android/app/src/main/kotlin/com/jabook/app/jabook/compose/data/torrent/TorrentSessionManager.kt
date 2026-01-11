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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libtorrent4j.AlertListener
import org.libtorrent4j.LibTorrent
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
import org.libtorrent4j.alerts.DhtErrorAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PeerLogAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.StateChangedAlert
import org.libtorrent4j.alerts.StateUpdateAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentLogAlert
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages libtorrent4j session and torrent operations
 */
@Singleton
public class TorrentSessionManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        // Use SessionManager(false) like libretorrent to avoid automatic alert listener issues
        private var session: SessionManager? = null
        private val torrents = mutableMapOf<String, TorrentHandle>()
        private val topicIds = mutableMapOf<String, String>()

        private val _downloadsFlow = MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())
        public val downloadsFlow: StateFlow<Map<String, TorrentDownload>> = _downloadsFlow.asStateFlow()

        private val alertListener =
            object : AlertListener {
                override fun types(): IntArray? {
                    // Specify alert types explicitly like libretorrent does
                    // This is more efficient and avoids potential issues with null
                    return intArrayOf(
                        AlertType.ADD_TORRENT.swig(),
                        AlertType.METADATA_RECEIVED.swig(),
                        AlertType.STATE_CHANGED.swig(),
                        AlertType.TORRENT_FINISHED.swig(),
                        AlertType.TORRENT_ERROR.swig(),
                        AlertType.BLOCK_FINISHED.swig(),
                        AlertType.PIECE_FINISHED.swig(),
                        AlertType.DHT_ERROR.swig(),
                        AlertType.STATE_UPDATE.swig(),
                        AlertType.PEER_LOG.swig(),
                        AlertType.TORRENT_LOG.swig(),
                    )
                }

                override fun alert(alert: Alert<*>) {
                    try {
                        public val alertType = alert.type()

                        // Handle specific alert types
                        when (alert) {
                            is AddTorrentAlert -> handleAddTorrent(alert)
                            is StateChangedAlert -> handleStateChanged(alert)
                            is TorrentFinishedAlert -> handleTorrentFinished(alert)
                            is TorrentErrorAlert -> handleTorrentError(alert)
                            is MetadataReceivedAlert -> handleMetadataReceived(alert)
                            is BlockFinishedAlert -> handleBlockFinished(alert)
                            is PieceFinishedAlert -> handlePieceFinished(alert)
                            is DhtErrorAlert -> handleDhtError(alert)
                            is StateUpdateAlert -> handleStateUpdate(alert)
                            is PeerLogAlert -> {
                                // Log peer-level debugging (can be verbose, so use debug level)
                                Log.v(TAG, "PEER_LOG: ${(alert as PeerLogAlert).logMessage()}")
                            }
                            is TorrentLogAlert -> {
                                // Log torrent-level debugging
                                Log.d(TAG, "TORRENT_LOG: ${(alert as TorrentLogAlert).logMessage()}")
                            }
                            else -> {
                                // Log unhandled alerts for debugging (use verbose to avoid spam)
                                Log.v(TAG, "Unhandled alert: ${alertType.name} - ${alert.message()}")
                            }
                        }
                    } catch (e: Exception) {
                        // Catch any exceptions in alert handling to prevent crashes
                        Log.e(TAG, "Error handling alert: ${alert.type().name}", e)
                    }
                }
            }

        /**
         * Initialize libtorrent session
         */
        public fun initSession(...) {
            if (session != null) {
                Log.w(TAG, "Session already initialized")
                return
            }

            try {
                // CRITICAL: Check if libtorrent4j classes are available before creating SessionManager
                // This helps catch NoSuchMethodError early, before static initialization
                try {
                    // Try to access a class that will trigger static initialization
                    // This will fail early if native library is incompatible
                    Class.forName("org.libtorrent4j.swig.alert")
                    Log.d(TAG, "libtorrent4j classes are available")
                } catch (e: NoClassDefFoundError) {
                    Log.e(TAG, "libtorrent4j classes not available - version mismatch", e)
                    session = null
                    return
                } catch (e: LinkageError) {
                    Log.e(TAG, "libtorrent4j linkage error during class check", e)
                    session = null
                    return
                } catch (e: NoSuchMethodError) {
                    Log.e(TAG, "libtorrent4j native method not found - version mismatch", e)
                    session = null
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify libtorrent4j classes, proceeding anyway", e)
                }

                // Log libtorrent version for debugging (as shown in examples)
                try {
                    public val version = LibTorrent.version()
                    Log.i(TAG, "Using libtorrent version: $version")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get libtorrent version", e)
                }

                public val settings =
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

                public val params = SessionParams(settings)
                // Use SessionManager(false) like libretorrent - this prevents automatic alert listener
                // which can cause NoSuchMethodError with some libtorrent4j versions
                session =
                    SessionManager(false).apply {
                        // Add listener BEFORE start() to ensure all alerts are captured
                        // This matches the pattern from libtorrent4j examples
                        addListener(alertListener)
                        start(params)
                    }

                // Verify session is running before proceeding
                // Note: isRunning() may not be available in all libtorrent4j versions
                try {
                    public val isRunning = session?.isRunning() ?: false
                    if (!isRunning) {
                        Log.e(TAG, "Session failed to start - isRunning() returned false")
                        throw IllegalStateException("Session failed to start")
                    }
                } catch (e: NoSuchMethodError) {
                    // isRunning() not available in this version, assume session started if no exception
                    Log.d(TAG, "isRunning() not available, assuming session started successfully")
                }

                Log.i(TAG, "Torrent session initialized successfully")
            } catch (e: NoClassDefFoundError) {
                Log.e(TAG, "libtorrent4j classes not available - version mismatch", e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: LinkageError) {
                Log.e(TAG, "libtorrent4j linkage error - version mismatch", e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: NoSuchMethodError) {
                Log.e(TAG, "libtorrent4j version mismatch - native library incompatible", e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libtorrent4j native library", e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize torrent session", e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue
            }
        }

        /**
         * Add torrent from magnet URI
         */
        public fun addTorrent(
            magnetUri: String,
            savePath: String,
            selectedFileIndices: List<Int>? = null,
            topicId: String? = null,
        ): Result<String> {
            public val session =
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
                Log.d(TAG, "addTorrent called: magnetUri=${magnetUri.take(100)}..., savePath=$savePath, topicId=$topicId")

                // Validate magnet URI format
                if (!magnetUri.startsWith("magnet:", ignoreCase = true)) {
                    Log.e(TAG, "Invalid magnet URI format: $magnetUri")
                    return Result.failure(IllegalArgumentException("Invalid magnet URI format. Must start with 'magnet:'"))
                }

                // Parse magnet URI to get info hash
                public val hash =
                    parseMagnetHash(magnetUri)
                        ?: return Result.failure(IllegalArgumentException("Invalid magnet URI: cannot parse info hash"))

                Log.d(TAG, "Parsed magnet URI: hash=$hash")

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
                public val saveDir = File(savePath)
                if (!saveDir.exists()) {
                    public val created = saveDir.mkdirs()
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

                // Verify session is running before adding torrent
                // Note: isRunning() may not be available in all libtorrent4j versions
                try {
                    public val isRunning = session.isRunning()
                    if (!isRunning) {
                        Log.e(TAG, "Cannot add torrent: session is not running")
                        return Result.failure(IllegalStateException("Session is not running"))
                    }
                    Log.d(TAG, "Session is running: $isRunning")
                } catch (e: NoClassDefFoundError) {
                    Log.e(TAG, "libtorrent4j classes not available when checking session", e)
                    return Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
                } catch (e: LinkageError) {
                    Log.e(TAG, "libtorrent4j linkage error when checking session", e)
                    return Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
                } catch (e: NoSuchMethodError) {
                    // isRunning() not available, assume session is running if no exception
                    Log.d(TAG, "isRunning() not available, assuming session is running")
                }

                // Add torrent - download(String magnetUri, File saveDir, torrent_flags_t flags)
                // Using empty flags (defaults) - SessionManager will handle magnet URI parsing
                // Wrap in try-catch to handle any native exceptions
                try {
                    Log.d(TAG, "Calling session.download() for hash=$hash, savePath=$savePath")

                    // Create flags - this may fail if libtorrent4j classes are not available
                    public val flags =
                        try {
                            org.libtorrent4j.swig.torrent_flags_t()
                        } catch (e: NoClassDefFoundError) {
                            Log.e(TAG, "libtorrent4j classes not available - version mismatch", e)
                            return Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
                        } catch (e: LinkageError) {
                            Log.e(TAG, "libtorrent4j linkage error - version mismatch", e)
                            return Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
                        }

                    session.download(magnetUri, saveDir, flags)
                    Log.i(TAG, "Successfully called session.download() for hash=$hash. Waiting for ADD_TORRENT alert...")
                    // Note: The actual torrent handle will be available in ADD_TORRENT alert
                    // We return the hash now, but the torrent won't be in torrents map until alert fires
                    Result.success(hash)
                } catch (e: NoClassDefFoundError) {
                    Log.e(TAG, "Class not found error while adding torrent: hash=$hash", e)
                    Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
                } catch (e: LinkageError) {
                    Log.e(TAG, "Linkage error while adding torrent: hash=$hash", e)
                    Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library error while adding torrent: hash=$hash", e)
                    Result.failure(IllegalStateException("Native library error: ${e.message}", e))
                } catch (e: NoSuchMethodError) {
                    Log.e(TAG, "Method not found error while adding torrent: hash=$hash", e)
                    Result.failure(IllegalStateException("Library version mismatch: ${e.message}", e))
                } catch (e: RuntimeException) {
                    // libtorrent4j may throw RuntimeException for various errors
                    Log.e(TAG, "Runtime error while adding torrent: hash=$hash, error=${e.message}", e)
                    Result.failure(IllegalStateException("Failed to add torrent: ${e.message}", e))
                }
            } catch (e: NoClassDefFoundError) {
                Log.e(TAG, "Class not found error while adding torrent", e)
                Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
            } catch (e: LinkageError) {
                Log.e(TAG, "Linkage error while adding torrent", e)
                Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
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
        public fun removeTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            public val handle = torrents.remove(hash) ?: return
            topicIds.remove(hash)

            try {
                session?.remove(handle)

                if (deleteFiles) {
                    public val savePath = handle.savePath()
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
        public fun pauseTorrent(...) {
            public val handle = torrents[hash] ?: return

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
        public fun resumeTorrent(...) {
            public val handle = torrents[hash] ?: return

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
        public fun moveTorrentStorage(
            hash: String,
            newPath: String,
        ) {
            public val handle = torrents[hash] ?: return
            public val newDir = File(newPath)
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
        public fun setSequentialDownload(
            hash: String,
            enabled: Boolean,
        ) {
            public val handle = torrents[hash] ?: return

            try {
                // setFlags(flags, mask) - use TorrentFlags
                public val flags =
                    if (enabled) {
                        org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
                    } else {
                        org.libtorrent4j.swig
                            .torrent_flags_t()
                    }
                public val mask = org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
                handle.setFlags(flags, mask)
                Log.i(TAG, "Set sequential download for $hash: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set sequential download", e)
            }
        }

        /**
         * Pause all torrents
         */
        public fun pauseAll(...) {
            torrents.values.forEach { it.pause() }
            updateDownloads()
        }

        /**
         * Resume all torrents
         */
        public fun resumeAll(...) {
            torrents.values.forEach { it.resume() }
            updateDownloads()
        }

        /**
         * Get current download info
         */
        public fun getDownload(hash: String): TorrentDownload? = _downloadsFlow.value[hash]

        /**
         * Stop session and cleanup
         */
        public fun stopSession(...) {
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
                public val handle = alert.handle()
                if (!handle.isValid) {
                    Log.e(TAG, "Invalid torrent handle in ADD_TORRENT alert")
                    return
                }

                public val hash = handle.infoHash().toHex()
                public val status = handle.status()
                public val torrentInfo = handle.torrentFile()

                Log.i(
                    TAG,
                    "Torrent added: hash=$hash, " +
                        "name='${torrentInfo?.name() ?: "unknown"}', " +
                        "state=${status.state()}, " +
                        "files=${torrentInfo?.numFiles() ?: 0}, " +
                        "size=${torrentInfo?.totalSize() ?: 0} bytes",
                )

                torrents[hash] = handle

                // Resume torrent to start downloading (required by libtorrent4j)
                // According to libtorrent4j examples, handle.resume() must be called after adding
                // But we need to be careful - if handle is invalid or session is not running, this will crash
                try {
                    // Double-check handle is still valid before resuming
                    if (handle.isValid) {
                        // Check if session is running (if method available)
                        public val sessionRunning =
                            try {
                                session?.isRunning() ?: true
                            } catch (e: NoSuchMethodError) {
                                true // Assume running if method not available
                            }

                        if (sessionRunning) {
                            handle.resume()
                            Log.d(TAG, "Torrent resumed after add: $hash")
                        } else {
                            Log.w(TAG, "Cannot resume torrent: session is not running")
                        }
                    } else {
                        Log.w(TAG, "Cannot resume torrent: handle is invalid")
                    }
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library error resuming torrent: $hash", e)
                } catch (e: NoSuchMethodError) {
                    Log.e(TAG, "Method not found error resuming torrent: $hash", e)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Runtime error resuming torrent: $hash, error=${e.message}", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resume torrent after add: $hash, error=${e.message}", e)
                }

                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleAddTorrent: ${e.message}", e)
            }
        }

        private fun handleStateChanged(alert: StateChangedAlert) {
            try {
                public val handle = alert.handle()
                if (handle.isValid) {
                    public val hash = handle.infoHash().toHex()
                    public val status = handle.status()
                    public val oldState = alert.prevState
                    public val newState = status.state()

                    if (oldState != newState) {
                        Log.d(
                            TAG,
                            "State changed for $hash: " +
                                "$oldState -> $newState, " +
                                "progress=${(status.progress() * 100).toInt()}%",
                        )
                    }
                }
                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling state changed alert", e)
            }
        }

        private fun handleTorrentFinished(alert: TorrentFinishedAlert) {
            try {
                public val handle = alert.handle()
                if (handle.isValid) {
                    public val hash = handle.infoHash().toHex()
                    public val status = handle.status()
                    Log.i(
                        TAG,
                        "Torrent finished: hash=$hash, " +
                            "downloaded=${status.totalDone()} bytes, " +
                            "uploaded=${status.totalUpload()} bytes, " +
                            "downloadRate=${status.downloadRate()} bytes/s",
                    )
                }
                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling torrent finished alert", e)
            }
        }

        private fun handleTorrentError(alert: TorrentErrorAlert) {
            try {
                public val handle = alert.handle()
                if (!handle.isValid) {
                    Log.e(TAG, "Torrent error alert with invalid handle")
                    return
                }

                public val hash = handle.infoHash().toHex()
                public val error = alert.error()
                public val status = handle.status()

                Log.e(
                    TAG,
                    "Torrent error for $hash: " +
                        "error='${error.message}', " +
                        "state=${status.state()}, " +
                        "progress=${(status.progress() * 100).toInt()}%, " +
                        "downloadRate=${status.downloadRate()} bytes/s, " +
                        "uploadRate=${status.uploadRate()} bytes/s, " +
                        "numPeers=${status.numPeers()}, " +
                        "numSeeds=${status.numSeeds()}",
                )
                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling torrent error alert", e)
            }
        }

        private fun handleMetadataReceived(alert: MetadataReceivedAlert) {
            try {
                public val handle = alert.handle()
                if (handle.isValid) {
                    public val hash = handle.infoHash().toHex()
                    public val torrentInfo = handle.torrentFile()
                    if (torrentInfo != null) {
                        Log.i(
                            TAG,
                            "Metadata received for $hash: name='${torrentInfo.name()}', files=${torrentInfo.numFiles()}, size=${torrentInfo.totalSize()} bytes",
                        )
                    } else {
                        Log.i(TAG, "Metadata received for $hash (torrent info not yet available)")
                    }
                }
                updateDownloads()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling metadata received alert", e)
            }
        }

        private fun handleBlockFinished(alert: BlockFinishedAlert) {
            // Update less frequently for performance
            if (System.currentTimeMillis() % 1000 < 100) {
                updateDownloads()
            }
        }

        private fun handlePieceFinished(alert: PieceFinishedAlert) {
            try {
                public val handle = alert.handle()
                if (handle.isValid) {
                    public val hash = handle.infoHash().toHex()
                    public val progress = (handle.status().progress() * 100).toInt()
                    public val pieceIndex = alert.pieceIndex()
                    Log.d(TAG, "Piece finished: hash=$hash, piece=$pieceIndex, progress=$progress%")
                }
                // Update downloads less frequently for performance
                if (System.currentTimeMillis() % 2000 < 200) {
                    updateDownloads()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling piece finished alert", e)
            }
        }

        private fun handleDhtError(alert: DhtErrorAlert) {
            public val error = alert.error()
            Log.w(TAG, "DHT error: ${error.message}")
            // DHT errors are usually non-critical, just log them
        }

        private fun handleStateUpdate(alert: StateUpdateAlert) {
            try {
                public val message = alert.message()
                Log.d(TAG, "State update: $message")
                // State updates can be frequent, so we don't update downloads on every one
                // The state changed alert will handle that
            } catch (e: Exception) {
                Log.e(TAG, "Error handling state update alert", e)
            }
        }

        // Helper methods

        private fun updateDownloads() {
            try {
                public val downloads =
                    torrents
                        .mapNotNull { (hash, handle) ->
                            try {
                                // Verify handle is still valid before creating download info
                                if (!handle.isValid) {
                                    Log.w(TAG, "Handle invalid for torrent $hash, skipping update")
                                    null
                                } else {
                                    hash to createTorrentDownload(hash, handle)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to create download info for torrent $hash", e)
                                null
                            }
                        }.toMap()
                _downloadsFlow.value = downloads
            } catch (e: Exception) {
                Log.e(TAG, "Critical error updating downloads", e)
                // Don't clear downloads on error, keep last known state
            }
        }

        private fun createTorrentDownload(
            hash: String,
            handle: TorrentHandle,
        ): TorrentDownload {
            try {
                public val status = handle.status()
                public val torrentInfo = handle.torrentFile()

                // Get name with fallback: try status.name(), then torrentInfo.name(), then hash
                public val name =
                    try {
                        public val statusName = status.name()
                        if (statusName.isNotBlank()) {
                            statusName
                        } else {
                            torrentInfo?.name()?.takeIf { it.isNotBlank() } ?: hash
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get name for torrent $hash, using hash as fallback", e)
                        hash
                    }

                // Get save path with error handling
                public val savePath =
                    try {
                        handle.savePath()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get save path for torrent $hash", e)
                        ""
                    }

                // Get progress with bounds checking
                public val progress = status.progress().coerceIn(0f, 1f)

                // Get speeds with error handling
                public val downloadSpeed =
                    try {
                        status.downloadRate().toLong().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get download speed for torrent $hash", e)
                        0L
                    }

                public val uploadSpeed =
                    try {
                        status.uploadRate().toLong().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get upload speed for torrent $hash", e)
                        0L
                    }

                // Get sizes with error handling
                public val totalSize =
                    try {
                        status.totalWanted().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get total size for torrent $hash", e)
                        0L
                    }

                public val downloadedSize =
                    try {
                        status.totalWantedDone().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get downloaded size for torrent $hash", e)
                        0L
                    }

                public val uploadedSize =
                    try {
                        status.allTimeUpload().coerceAtLeast(0L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get uploaded size for torrent $hash", e)
                        0L
                    }

                // Get peer counts with error handling
                public val numPeers =
                    try {
                        status.numPeers().coerceAtLeast(0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get num peers for torrent $hash", e)
                        0
                    }

                public val numSeeds =
                    try {
                        status.numSeeds().coerceAtLeast(0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get num seeds for torrent $hash", e)
                        0
                    }

                // Calculate ETA with error handling
                public val eta =
                    try {
                        calculateEta(status)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to calculate ETA for torrent $hash", e)
                        -1L
                    }

                // Get files with error handling
                public val files =
                    try {
                        if (torrentInfo != null) {
                            mapFiles(torrentInfo, handle)
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to map files for torrent $hash", e)
                        emptyList()
                    }

                return TorrentDownload(
                    hash = hash,
                    name = name,
                    state = mapState(status.state()),
                    progress = progress,
                    downloadSpeed = downloadSpeed,
                    uploadSpeed = uploadSpeed,
                    totalSize = totalSize,
                    downloadedSize = downloadedSize,
                    uploadedSize = uploadedSize,
                    numPeers = numPeers,
                    numSeeds = numSeeds,
                    eta = eta,
                    savePath = savePath,
                    files = files,
                    errorMessage = null, // Error tracking not available in current libtorrent4j binding
                    topicId = topicIds[hash],
                )
            } catch (e: Exception) {
                // If anything goes wrong, return a minimal valid TorrentDownload
                Log.e(TAG, "Critical error creating TorrentDownload for $hash", e)
                return TorrentDownload(
                    hash = hash,
                    name = hash, // Fallback to hash
                    state = TorrentState.ERROR,
                    progress = 0f,
                    downloadSpeed = 0L,
                    uploadSpeed = 0L,
                    totalSize = 0L,
                    downloadedSize = 0L,
                    uploadedSize = 0L,
                    numPeers = 0,
                    numSeeds = 0,
                    eta = -1L,
                    savePath = "",
                    files = emptyList(),
                    errorMessage = "Error creating download info: ${e.message}",
                    topicId = topicIds[hash],
                )
            }
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
        public fun prioritizeFile(
            hash: String,
            fileIndex: Int,
            priority: Int,
        ) {
            public val handle = torrents[hash] ?: return
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
        public fun setFilePriorities(
            hash: String,
            priorities: List<Int>,
        ) {
            public val handle = torrents[hash] ?: return
            public val torrentInfo = handle.torrentFile() ?: return
            public val numFiles = torrentInfo.numFiles()

            if (priorities.size != numFiles) {
                Log.w(TAG, "Priority list size mismatch: ${priorities.size} != $numFiles")
                return
            }

            try {
                // Priority.fromSwig expects int
                public val priorityArray = priorities.map { org.libtorrent4j.Priority.fromSwig(it) }.toTypedArray()
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
            return try {
                public val fileStorage = torrentInfo.files() ?: return emptyList()
                public val numFiles = fileStorage.numFiles()

                if (numFiles <= 0) {
                    Log.w(TAG, "Torrent has no files")
                    return emptyList()
                }

                // Get priorities with error handling
                public val priorities =
                    try {
                        handle.filePriorities() // Returns Priority[]
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get file priorities", e)
                        emptyArray()
                    }

                // Get progress with error handling
                public val progress =
                    try {
                        // Use empty flags to get progress in bytes, not pieces
                        handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get file progress", e)
                        longArrayOf()
                    }

                (0 until numFiles).mapNotNull { index ->
                    try {
                        public val priority =
                            if (index < priorities.size) {
                                try {
                                    priorities[index].swig().toInt().coerceIn(0, 7)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to get priority for file $index", e)
                                    4 // Default priority
                                }
                            } else {
                                4 // Default priority
                            }

                        public val size =
                            try {
                                fileStorage.fileSize(index).coerceAtLeast(0L)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get size for file $index", e)
                                0L
                            }

                        public val downloaded =
                            if (index < progress.size) {
                                try {
                                    progress[index].coerceAtLeast(0L)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to get downloaded bytes for file $index", e)
                                    0L
                                }
                            } else {
                                0L
                            }

                        public val fileProgress = if (size > 0) (downloaded.toFloat() / size).coerceIn(0f, 1f) else 0f

                        public val path =
                            try {
                                fileStorage.filePath(index) ?: "file_$index"
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to get path for file $index", e)
                                "file_$index"
                            }

                        TorrentFile(
                            index = index,
                            path = path,
                            size = size,
                            priority = priority,
                            progress = fileProgress,
                            isSelected = priority != 0,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to map file $index", e)
                        null // Skip this file
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical error mapping files", e)
                emptyList()
            }
        }

        /**
         * Check if file is ready for streaming (first chunk downloaded)
         * @param bufferSize bytes to check (default 10MB)
         */
        public fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
            bufferSize: Int =  * 1024 * 1024L, // 10MB
        ): Boolean {
            public val handle = torrents[hash] ?: return false
            public val torrentInfo = handle.torrentFile() ?: return false
            public val fileStorage = torrentInfo.files() ?: return false

            if (fileIndex < 0 || fileIndex >= fileStorage.numFiles()) return false

            public val fileSize = fileStorage.fileSize(fileIndex)
            public val checkSize = minOf(fileSize, bufferSize)

            // If file is very small or fully downloaded, it's ready
            public val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
            public val downloadedBytes = if (fileIndex < progress.size) progress[fileIndex] else 0L

            if (downloadedBytes >= fileSize) return true

            // Check specific pieces
            // We need to map file offset to pieces
            public val fileOffset = fileStorage.fileOffset(fileIndex)
            public val startPiece = torrentInfo.mapFile(fileIndex, 0, 0).piece()
            // We only check the beginning of the file for "start" capability
            public val endOffsetInFile = minOf(fileSize, bufferSize)
            public val endPiece = torrentInfo.mapFile(fileIndex, endOffsetInFile, 0).piece()

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
        public fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long {
            public val handle = torrents[hash] ?: return 0L
            public val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
            return if (fileIndex < progress.size) progress[fileIndex] else 0L
        }

        private fun calculateEta(status: TorrentStatus): Long {
            public val remaining = status.totalWanted() - status.totalWantedDone()
            public val speed = status.downloadRate()

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
                    public val hexRegex = "urn:btih:([a-fA-F0-9]{40})".toRegex()
                    public val base32Regex = "urn:btih:([a-zA-Z2-7]{32})".toRegex()

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
                    public val anyHashRegex = "(?:urn:btih:|btih:)?([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})".toRegex(RegexOption.IGNORE_CASE)
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

        public companion object {
            private const val TAG = "TorrentSessionManager"
        }
    }
