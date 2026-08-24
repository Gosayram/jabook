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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.storage.AtomicFileWriter
import com.jabook.app.jabook.compose.data.worker.LibraryScanWorker
import com.jabook.app.jabook.compose.data.worker.WorkConstraintsPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.BlockFinishedAlert
import org.libtorrent4j.alerts.DhtErrorAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.SaveResumeDataFailedAlert
import org.libtorrent4j.alerts.StateChangedAlert
import org.libtorrent4j.alerts.StateUpdateAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TrackerAnnounceAlert
import org.libtorrent4j.alerts.TrackerErrorAlert
import org.libtorrent4j.alerts.TrackerReplyAlert
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        private val loggerFactory: LoggerFactory,
        private val torrentDownloadDao: TorrentDownloadDao,
        private val torrentResumeDao: TorrentResumeDao,
        private val stateBuilder: TorrentStateBuilder,
    ) {
        private val logger = loggerFactory.get("TorrentSessionManager")

        // Use SessionManager(false) like libretorrent to avoid automatic alert listener issues
        private var session: SessionManager? = null
        private val torrents = ConcurrentHashMap<String, TorrentHandle>()
        private val topicIds = ConcurrentHashMap<String, String>()

        // Hashes added via addTorrent() whose ADD_TORRENT alert has not fired yet.
        // Guards restoreActiveDownloads() (running concurrently after session init)
        // from re-adding a just-added torrent via a second code path.
        private val pendingAdds = ConcurrentHashMap.newKeySet<String>()
        private var lastLibrarySyncTriggerAtMs: Long = 0L

        private val _downloadsFlow = MutableStateFlow<Map<String, TorrentDownload>>(emptyMap())
        public val downloadsFlow: StateFlow<Map<String, TorrentDownload>> = _downloadsFlow.asStateFlow()

        private var sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Kept separate from sessionScope: stopSession() cancels alert processing, but must not
        // cancel a state snapshot already requested by a memory-pressure callback.
        private val statePersistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Tracks pending SaveResumeDataAlerts so stopSession() can await them before shutting down.
        private var pendingResumeDataLatch: CountDownLatch? = null

        private companion object {
            private const val LIBRARY_SYNC_TRIGGER_COOLDOWN_MS = 3_000L
            private const val SESSION_STATE_DIRECTORY = "torrent"
            private const val SESSION_STATE_FILE = "session.state"

            // Fallback trackers used when magnet link has no tracker URLs
            private val FALLBACK_TRACKERS =
                listOf(
                    "udp://tracker.opentrackr.org:1337/announce",
                    "udp://open.stealth.si:80/announce",
                    "udp://tracker.openbittorrent.com:6969/announce",
                    "udp://open.demonii.com:1337/announce",
                    "udp://exodus.desync.com:6969/announce",
                )
        }

        private val alertListener =
            object : AlertListener {
                override fun types(): IntArray? {
                    // Specify alert types explicitly like libretorrent does
                    // This is more efficient and avoids potential issues with null.
                    // Log alerts (PeerLog/TorrentLog/DhtLog) are excluded — SessionManager(false)
                    // already removes log categories from the alert mask, so registering them
                    // here would be dead code that can never fire.
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
                        AlertType.SAVE_RESUME_DATA.swig(),
                        AlertType.SAVE_RESUME_DATA_FAILED.swig(),
                        AlertType.TRACKER_REPLY.swig(),
                        AlertType.TRACKER_ERROR.swig(),
                        AlertType.TRACKER_ANNOUNCE.swig(),
                    )
                }

                override fun alert(alert: Alert<*>) {
                    try {
                        val alertType = alert.type()

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
                            is SaveResumeDataAlert -> handleSaveResumeData(alert)
                            is SaveResumeDataFailedAlert -> {
                                logger.w {
                                    "Save resume data failed for ${alert.handle().infoHash().toHex()}"
                                }
                                // Failed saves must also release the latch, or stopSession() waits the full timeout.
                                pendingResumeDataLatch?.countDown()
                            }
                            is TrackerReplyAlert -> {
                                val hash = alert.handle().infoHash().toHex()
                                logger.i { "TRACKER_REPLY for $hash: ${alert.numPeers()} peers from ${alert.trackerUrl()}" }
                            }
                            is TrackerErrorAlert -> {
                                val hash = alert.handle().infoHash().toHex()
                                logger.w { "TRACKER_ERROR for $hash: ${alert.errorMessage()} from ${alert.trackerUrl()}" }
                            }
                            is TrackerAnnounceAlert -> {
                                val hash = alert.handle().infoHash().toHex()
                                logger.d { "TRACKER_ANNOUNCE for $hash: ${alert.trackerUrl()}" }
                            }
                            else -> {
                                // Log unhandled alerts for debugging (use debug to avoid spam)
                                logger.d { "Unhandled alert: ${alertType.name} - ${alert.message()}" }
                            }
                        }
                    } catch (e: Exception) {
                        // Catch any exceptions in alert handling to prevent crashes
                        logger.e({ "Error handling alert: ${alert.type().name}" }, e)
                    }
                }
            }

        /**
         * Initialize libtorrent session
         */
        public fun initSession() {
            if (session != null) {
                logger.w { "Session already initialized" }
                return
            }
            if (!sessionScope.isActive) {
                sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }

            try {
                // CRITICAL: Check if libtorrent4j classes are available before creating SessionManager
                // This helps catch NoSuchMethodError early, before static initialization
                try {
                    // Try to access a class that will trigger static initialization
                    // This will fail early if native library is incompatible
                    Class.forName("org.libtorrent4j.swig.alert")
                    logger.d { "libtorrent4j classes are available" }
                } catch (e: NoClassDefFoundError) {
                    logger.e({ "libtorrent4j classes not available - version mismatch" }, e)
                    session = null
                    return
                } catch (e: NoSuchMethodError) {
                    logger.e({ "libtorrent4j native method not found - version mismatch" }, e)
                    session = null
                    return
                } catch (e: LinkageError) {
                    logger.e({ "libtorrent4j linkage error during class check" }, e)
                    session = null
                    return
                } catch (e: Exception) {
                    logger.e({ "Could not verify libtorrent4j classes, proceeding anyway" }, e)
                }

                // Log libtorrent version for debugging (as shown in examples)
                try {
                    val version = LibTorrent.version()
                    logger.i { "Using libtorrent version: $version" }
                } catch (e: Exception) {
                    logger.e({ "Could not get libtorrent version" }, e)
                }

                val settings =
                    SettingsPack().apply {
                        // Connection settings
                        connectionsLimit(200)
                        downloadRateLimit(0) // Unlimited by default
                        uploadRateLimit(0) // Unlimited by default

                        // Listen on a port range to avoid ISP blocks on default port
                        listenInterfaces("0.0.0.0:6881-6889")

                        // DHT and other settings are enabled by default in libtorrent4j
                        // Just keeping defaults

                        // Performance settings
                        activeDownloads(4)
                        activeSeeds(4)
                        activeLimit(8)

                        // Memory guardrails — prevent unbounded memory growth during long sessions
                        sendBufferWatermark(1024 * 1024) // 1 MiB send buffer watermark
                        activeDhtLimit(80) // cap DHT routing-table peers
                        try {
                            setInteger(settings_pack.int_types.max_out_request_queue.swigValue(), 100)
                        } catch (_: NoSuchMethodError) {
                            // older libtorrent4j versions lack max_out_request_queue — safe to skip
                        }

                        // Best practices (libtorrent4j): improve peer discovery and resist
                        // ISP throttling without forcing encryption (which would shrink
                        // the reachable peer pool).
                        try {
                            setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                            setBoolean(settings_pack.bool_types.strict_end_game_mode.swigValue(), true)
                            setBoolean(settings_pack.bool_types.announce_crypto_support.swigValue(), true)
                            setBoolean(settings_pack.bool_types.prefer_rc4.swigValue(), true)
                        } catch (_: NoSuchMethodError) {
                            // older libtorrent4j versions lack these flags — safe to skip
                        }
                    }

                val params = createSessionParams(settings)
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
                    val isRunning = session?.isRunning() ?: false
                    if (!isRunning) {
                        logger.e { "Session failed to start - isRunning() returned false" }
                        throw IllegalStateException("Session failed to start")
                    }
                } catch (e: NoSuchMethodError) {
                    // isRunning() not available in this version, assume session started if no exception
                    logger.d { "isRunning() not available, assuming session started successfully" }
                }

                logger.i { "Torrent session initialized successfully" }
            } catch (e: NoClassDefFoundError) {
                logger.e({ "libtorrent4j classes not available - version mismatch" }, e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: NoSuchMethodError) {
                logger.e({ "libtorrent4j version mismatch - native library incompatible" }, e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: UnsatisfiedLinkError) {
                logger.e({ "Failed to load libtorrent4j native library" }, e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue
            } catch (e: LinkageError) {
                logger.e({ "libtorrent4j linkage error - version mismatch" }, e)
                session = null // Ensure session is null on error
                // Don't throw - allow app to continue without torrent functionality
                // User will see error when trying to download
            } catch (e: Exception) {
                logger.e({ "Failed to initialize torrent session" }, e)
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
            val session =
                this.session ?: run {
                    // Try to initialize if not already done
                    try {
                        initSession()
                    } catch (e: Exception) {
                        logger.e({ "Failed to initialize session for addTorrent" }, e)
                    }
                    return@run this.session
                } ?: return Result.failure(
                    IllegalStateException("Session not initialized - libtorrent4j may not be available"),
                )

            return try {
                logger.d {
                    "addTorrent called: magnetUri=${magnetUri.take(
                        100,
                    )}..., savePath=$savePath, topicId=$topicId"
                }

                // Validate magnet URI format
                if (!MagnetUriValidationPolicy.isValidMagnetUri(magnetUri)) {
                    logger.e { "Invalid magnet URI format: $magnetUri" }
                    return Result.failure(
                        IllegalArgumentException("Invalid magnet URI format. Expected magnet:?xt=urn:btih:<hash>"),
                    )
                }

                // Parse magnet URI to get info hash
                val hash =
                    parseMagnetHash(magnetUri)
                        ?: return Result.failure(IllegalArgumentException("Invalid magnet URI: cannot parse info hash"))

                logger.d { "Parsed magnet URI: hash=$hash" }

                // Check if already added
                if (torrents.containsKey(hash)) {
                    logger.w { "Torrent already added: $hash" }
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
                        logger.e { "Failed to create save directory: $savePath" }
                        return Result.failure(IllegalStateException("Failed to create save directory: $savePath"))
                    }
                }

                // Verify directory is writable
                if (!saveDir.canWrite()) {
                    logger.e { "Save directory is not writable: $savePath" }
                    return Result.failure(IllegalStateException("Save directory is not writable: $savePath"))
                }

                // Verify session is running before adding torrent
                // Note: isRunning() may not be available in all libtorrent4j versions
                try {
                    val isRunning = session.isRunning()
                    if (!isRunning) {
                        logger.e { "Cannot add torrent: session is not running" }
                        return Result.failure(IllegalStateException("Session is not running"))
                    }
                    logger.d { "Session is running: $isRunning" }
                } catch (e: NoClassDefFoundError) {
                    logger.e({ "libtorrent4j classes not available when checking session" }, e)
                    return Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
                } catch (e: NoSuchMethodError) {
                    // isRunning() not available, assume session is running if no exception
                    logger.d { "isRunning() not available, assuming session is running" }
                } catch (e: LinkageError) {
                    logger.e({ "libtorrent4j linkage error when checking session" }, e)
                    return Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
                }

                // Add torrent - download(String magnetUri, File saveDir, torrent_flags_t flags)
                // Using empty flags (defaults) - SessionManager will handle magnet URI parsing
                // Wrap in try-catch to handle any native exceptions
                try {
                    // Append fallback trackers if magnet has no tracker URLs
                    val effectiveMagnetUri =
                        if (!magnetUri.contains("&tr=") && !magnetUri.contains("&tr%3D")) {
                            val trackerSuffix =
                                FALLBACK_TRACKERS.joinToString("&") { "tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
                            "$magnetUri&$trackerSuffix"
                        } else {
                            magnetUri
                        }

                    logger.d { "Calling session.download() for hash=$hash, savePath=$savePath" }

                    // Create flags - this may fail if libtorrent4j classes are not available
                    val flags =
                        try {
                            org.libtorrent4j.swig.torrent_flags_t()
                        } catch (e: NoClassDefFoundError) {
                            logger.e({ "libtorrent4j classes not available - version mismatch" }, e)
                            return Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
                        } catch (e: LinkageError) {
                            logger.e({ "libtorrent4j linkage error - version mismatch" }, e)
                            return Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
                        }

                    session.download(effectiveMagnetUri, saveDir, flags)
                    // Track as pending until ADD_TORRENT fires, so a concurrent
                    // restoreActiveDownloads() doesn't re-add it via a second path.
                    pendingAdds.add(hash)
                    logger.i {
                        "Successfully called session.download() for hash=$hash. Waiting for ADD_TORRENT alert..."
                    }
                    // Persist a placeholder row immediately so a process death before the
                    // ADD_TORRENT alert cannot lose the download (the alert overwrites it).
                    sessionScope.launch {
                        try {
                            torrentDownloadDao.insertPendingRow(
                                hash = hash,
                                savePath = savePath,
                                topicId = topicId,
                                now = System.currentTimeMillis(),
                            )
                        } catch (e: Exception) {
                            logger.e({ "Failed to persist pending torrent row for $hash" }, e)
                        }
                    }
                    // Note: The actual torrent handle will be available in ADD_TORRENT alert
                    // We return the hash now, but the torrent won't be in torrents map until alert fires
                    Result.success(hash)
                } catch (e: NoClassDefFoundError) {
                    logger.e({ "Class not found error while adding torrent: hash=$hash" }, e)
                    Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
                } catch (e: UnsatisfiedLinkError) {
                    logger.e({ "Native library error while adding torrent: hash=$hash" }, e)
                    Result.failure(IllegalStateException("Native library error: ${e.message}", e))
                } catch (e: NoSuchMethodError) {
                    logger.e({ "Method not found error while adding torrent: hash=$hash" }, e)
                    Result.failure(IllegalStateException("Library version mismatch: ${e.message}", e))
                } catch (e: LinkageError) {
                    logger.e({ "Linkage error while adding torrent: hash=$hash" }, e)
                    Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
                } catch (e: RuntimeException) {
                    // libtorrent4j may throw RuntimeException for various errors
                    logger.e({ "Runtime error while adding torrent: hash=$hash, error=${e.message}" }, e)
                    Result.failure(IllegalStateException("Failed to add torrent: ${e.message}", e))
                }
            } catch (e: NoClassDefFoundError) {
                logger.e({ "Class not found error while adding torrent" }, e)
                Result.failure(IllegalStateException("libtorrent4j not available: ${e.message}", e))
            } catch (e: LinkageError) {
                logger.e({ "Linkage error while adding torrent" }, e)
                Result.failure(IllegalStateException("libtorrent4j linkage error: ${e.message}", e))
            } catch (e: IllegalStateException) {
                logger.e({ "Illegal state while adding torrent" }, e)
                Result.failure(e)
            } catch (e: IllegalArgumentException) {
                logger.e({ "Invalid argument while adding torrent" }, e)
                Result.failure(e)
            } catch (e: Exception) {
                logger.e({ "Unexpected error while adding torrent" }, e)
                Result.failure(e)
            }
        }

        /**
         * Remove torrent
         */
        @Synchronized
        public fun removeTorrent(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            val handle = torrents[hash] ?: return

            try {
                // Capture the save path BEFORE removing: after session.remove(handle)
                // the handle is invalid and savePath() would throw.
                val savePath =
                    if (deleteFiles) {
                        runCatching { handle.savePath() }.getOrNull()
                    } else {
                        null
                    }

                // Remove natively first: if that throws, the handle stays in the map so
                // stopSession() can still request its resume data and session.stop() cleans it up.
                session?.remove(handle)

                if (deleteFiles && savePath != null) {
                    File(savePath).deleteRecursively()
                }

                torrents.remove(hash)
                topicIds.remove(hash)
                pendingAdds.remove(hash)

                // Persist the terminal state so restoreActiveDownloads() on the next
                // start doesn't silently re-add this torrent:
                //  - deleteFiles=true  -> the torrent is gone, delete the row (+resume).
                //  - deleteFiles=false -> stopped, keep files/history, mark STOPPED.
                // Uses statePersistenceScope: it survives stopSession()/shutdown(), which
                // cancels sessionScope (e.g. deleteAllTorrents() -> stopDownloadService()).
                statePersistenceScope.launch {
                    try {
                        if (deleteFiles) {
                            torrentResumeDao.deleteTorrent(torrentDownloadDao, hash)
                        } else {
                            torrentDownloadDao.updateState(hash, TorrentState.STOPPED)
                        }
                    } catch (e: Exception) {
                        logger.w({ "Failed to persist terminal state for $hash" }, e)
                    }
                }

                updateDownloads()
                logger.i { "Removed torrent: $hash (deleteFiles=$deleteFiles)" }
            } catch (e: Exception) {
                logger.e({ "Failed to remove torrent" }, e)
            }
        }

        /**
         * Pause torrent
         */
        @Synchronized
        public fun pauseTorrent(hash: String) {
            val handle = torrents[hash] ?: return

            try {
                handle.pause()
                updateDownloads()
                logger.i { "Paused torrent: $hash" }
            } catch (e: Exception) {
                logger.e({ "Failed to pause torrent" }, e)
            }
        }

        /**
         * Resume torrent
         */
        @Synchronized
        public fun resumeTorrent(hash: String) {
            val handle = torrents[hash] ?: return

            try {
                handle.resume()
                updateDownloads()
                logger.i { "Resumed torrent: $hash" }
            } catch (e: Exception) {
                logger.e({ "Failed to resume torrent" }, e)
            }
        }

        /**
         * Enable sequential download for streaming
         */
        public fun setSequentialDownload(
            hash: String,
            enabled: Boolean,
        ) {
            val handle = torrents[hash] ?: return

            try {
                // setFlags(flags, mask) - use TorrentFlags
                val flags =
                    if (enabled) {
                        org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
                    } else {
                        org.libtorrent4j.swig
                            .torrent_flags_t()
                    }
                val mask = org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
                handle.setFlags(flags, mask)
                logger.i { "Set sequential download for $hash: $enabled" }
            } catch (e: Exception) {
                logger.e({ "Failed to set sequential download" }, e)
            }
        }

        /**
         * Pause all torrents
         */
        @Synchronized
        public fun pauseAll() {
            torrents.values.toList().forEach { it.pause() }
            updateDownloads()
        }

        /**
         * Resume all torrents
         */
        @Synchronized
        public fun resumeAll() {
            torrents.values.toList().forEach { it.resume() }
            updateDownloads()
        }

        /**
         * Pauses libtorrent's native session and snapshots its session state when Android
         * reports critical memory pressure. This is deliberately session-level: pausing
         * individual handles leaves native networking and DHT buffers allocated.
         *
         * The operation is synchronized with other lifecycle work so a trim callback cannot
         * race session shutdown or a second trim callback.
         */
        @Synchronized
        public fun pauseForMemoryPressure() {
            val activeSession = session ?: return

            try {
                activeSession.pause()
                val state = activeSession.saveState()
                statePersistenceScope.launch {
                    persistSessionState(state)
                }
                logger.w { "Paused torrent session and saved native state after critical memory pressure" }
            } catch (e: Exception) {
                logger.e({ "Failed to guard torrent session after critical memory pressure" }, e)
            }
        }

        /**
         * Get current download info
         */
        public fun getDownload(hash: String): TorrentDownload? = _downloadsFlow.value[hash]

        /**
         * Stop session and cleanup.
         *
         * Requests resume data for all active torrents before stopping so that
         * downloads can be resumed on next session start without re-downloading
         * already-completed pieces.
         */
        @Synchronized
        public fun stopSession() {
            try {
                // Request resume data for all active handles before stopping.
                // saveResumeData is async — alerts arrive via the alert queue, so we
                // use a CountDownLatch to await all SaveResumeDataAlerts before stop().
                val handles = torrents.values.filter { it.isValid }
                if (handles.isNotEmpty()) {
                    val latch = CountDownLatch(handles.size)
                    pendingResumeDataLatch = latch
                    handles.forEach { handle ->
                        try {
                            handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
                        } catch (e: Exception) {
                            latch.countDown()
                            logger.w {
                                "Could not request resume data for ${handle.infoHash().toHex()}: ${e.message}"
                            }
                        }
                    }
                    // Wait up to 5 seconds for all SaveResumeDataAlerts to arrive
                    if (!latch.await(5, TimeUnit.SECONDS)) {
                        logger.w { "Timeout waiting for save resume data alerts (${handles.size} handles)" }
                    }
                }

                // Save session-level state (DHT routing table, peer lists) BEFORE stopping
                // so the next start boots with a warm DHT — libtorrent4j best practice.
                // Written on statePersistenceScope, which stopSession() does NOT cancel.
                try {
                    val state = session?.saveState()
                    if (state != null) {
                        statePersistenceScope.launch {
                            persistSessionState(state)
                        }
                        logger.i { "Saved torrent session state (${state.size} bytes)" }
                    }
                } catch (e: Exception) {
                    logger.w({ "Failed to save session state on shutdown" }, e)
                }

                torrents.clear()
                session?.stop()
                session = null
                sessionScope.cancel()
                logger.i { "Session stopped" }
            } catch (e: Exception) {
                logger.e({ "Error stopping session" }, e)
            } finally {
                pendingResumeDataLatch = null
            }
        }

        @Synchronized
        private fun persistSessionState(state: ByteArray) {
            try {
                val stateDirectory = File(context.filesDir, SESSION_STATE_DIRECTORY)
                if (!stateDirectory.exists() && !stateDirectory.mkdirs()) {
                    logger.w { "Unable to create torrent session state directory" }
                    return
                }

                val stateFile = File(stateDirectory, SESSION_STATE_FILE)
                AtomicFileWriter.writeAtomically(stateFile) { output ->
                    output.write(state)
                    state.size.toLong()
                }
            } catch (e: Exception) {
                logger.e({ "Failed to persist torrent session state" }, e)
            }
        }

        private fun createSessionParams(settings: SettingsPack): SessionParams {
            val stateFile = File(File(context.filesDir, SESSION_STATE_DIRECTORY), SESSION_STATE_FILE)
            if (!stateFile.exists()) return SessionParams(settings)

            return try {
                SessionParams(stateFile.readBytes()).apply {
                    // Current application limits take precedence over an older snapshot.
                    setSettings(settings)
                }
            } catch (e: Exception) {
                logger.w { "Ignoring invalid persisted torrent session state: ${e.message}" }
                stateFile.delete()
                SessionParams(settings)
            }
        }

        /**
         * Restores non-completed downloads from the database after a fresh session init.
         *
         * Downloads with persisted resume data are re-added using that data so libtorrent
         * can skip already-downloaded pieces. Downloads without resume data fall back to
         * re-adding via their magnet URI (they restart from scratch).
         */
        public fun restoreActiveDownloads() {
            sessionScope.launch {
                try {
                    val active = torrentDownloadDao.getActiveDownloads()
                    if (active.isEmpty()) return@launch
                    logger.i { "Restoring ${active.size} active torrent downloads" }
                    // Resume BLOBs are read in a single query (not on the list path).
                    val resumeDataByHash = torrentResumeDao.getAllResumeData().associate { it.hash to it.resumeData }
                    active.forEach { row ->
                        try {
                            if (torrents.containsKey(row.hash) || pendingAdds.contains(row.hash)) return@forEach
                            val resumeBytes = resumeDataByHash[row.hash]
                            if (resumeBytes != null) {
                                val byteVector = Vectors.bytes2byte_vector(resumeBytes)
                                val errorCode = error_code()
                                val swigParams = libtorrent.read_resume_data_ex(byteVector, errorCode)
                                if (errorCode.failed()) {
                                    logger.w { "Resume data rejected for ${row.hash}: ${errorCode.message()}" }
                                    return@forEach
                                }
                                val params =
                                    AddTorrentParams(swigParams).apply {
                                        setSavePath(row.savePath)
                                    }
                                session?.swig()?.async_add_torrent(params.swig())
                                logger.d { "Restored torrent with resume data: ${row.hash}" }
                            } else {
                                val magnetUri = "magnet:?xt=urn:btih:" + row.hash
                                val params = AddTorrentParams.parseMagnetUri(magnetUri)
                                params.setSavePath(row.savePath)
                                session?.swig()?.async_add_torrent(params.swig())
                                logger.d { "Restored torrent via magnet URI fallback: ${row.hash}" }
                            }
                        } catch (e: Exception) {
                            logger.e({ "Failed to restore torrent ${row.hash}" }, e)
                        }
                    }
                } catch (e: Exception) {
                    logger.e({ "Failed to restore active downloads" }, e)
                }
            }
        }

        // Alert handlers

        private fun handleSaveResumeData(alert: SaveResumeDataAlert) {
            try {
                val handle = alert.handle()
                if (!handle.isValid) return
                val hash = handle.infoHash().toHex()
                val resumeBytes = AddTorrentParams.writeResumeDataBuf(alert.params())
                // Write synchronously on the alert thread: stopSession() counts down the
                // same latch and then cancels sessionScope, so an async launch here could
                // be cancelled mid-write and lose the resume BLOB. The write targets the
                // tiny torrent_resume row, never the full torrent row.
                runBlocking {
                    try {
                        torrentResumeDao.updateResumeData(hash, resumeBytes)
                        logger.d { "Resume data saved for $hash (${resumeBytes.size} bytes)" }
                    } catch (e: Exception) {
                        logger.e({ "Failed to persist resume data for $hash" }, e)
                    }
                }
            } catch (e: Exception) {
                logger.e({ "Error handling SaveResumeDataAlert" }, e)
            } finally {
                pendingResumeDataLatch?.countDown()
            }
        }

        private fun handleAddTorrent(alert: AddTorrentAlert) {
            try {
                val handle = alert.handle()
                if (!handle.isValid) {
                    logger.e { "Invalid torrent handle in ADD_TORRENT alert" }
                    return
                }

                val hash = handle.infoHash().toHex()
                val status = handle.status()
                val torrentInfo = handle.torrentFile()

                logger.i {
                    "Torrent added: hash=$hash, " +
                        "name='${torrentInfo?.name() ?: "unknown"}', " +
                        "state=${status.state()}, " +
                        "files=${torrentInfo?.numFiles() ?: 0}, " +
                        "size=${torrentInfo?.totalSize() ?: 0} bytes"
                }

                torrents[hash] = handle
                pendingAdds.remove(hash)

                // Resume torrent to start downloading (required by libtorrent4j)
                // According to libtorrent4j examples, handle.resume() must be called after adding
                // But we need to be careful - if handle is invalid or session is not running, this will crash
                try {
                    // Double-check handle is still valid before resuming
                    if (handle.isValid) {
                        // Check if session is running (if method available)
                        val sessionRunning =
                            try {
                                session?.isRunning() ?: true
                            } catch (e: NoSuchMethodError) {
                                true // Assume running if method not available
                            }

                        if (sessionRunning) {
                            handle.resume()
                            logger.d { "Torrent resumed after add: $hash" }
                        } else {
                            logger.w { "Cannot resume torrent: session is not running" }
                        }
                    } else {
                        logger.w { "Cannot resume torrent: handle is invalid" }
                    }
                } catch (e: UnsatisfiedLinkError) {
                    logger.e({ "Native library error resuming torrent: $hash" }, e)
                } catch (e: NoSuchMethodError) {
                    logger.e({ "Method not found error resuming torrent: $hash" }, e)
                } catch (e: RuntimeException) {
                    logger.e({ "Runtime error resuming torrent: $hash, error=${e.message}" }, e)
                } catch (e: Exception) {
                    logger.e({ "Failed to resume torrent after add: $hash, error=${e.message}" }, e)
                }

                updateDownloads()
            } catch (e: Exception) {
                logger.e({ "Error in handleAddTorrent: ${e.message}" }, e)
            }
        }

        private fun handleStateChanged(alert: StateChangedAlert) {
            try {
                val handle = alert.handle()
                if (handle.isValid) {
                    val hash = handle.infoHash().toHex()
                    val status = handle.status()
                    val oldState = alert.prevState
                    val newState = status.state()

                    if (oldState != newState) {
                        logger.d {
                            "State changed for $hash: " +
                                "$oldState -> $newState, " +
                                "progress=${(status.progress() * 100).toInt()}%"
                        }
                    }
                }
                updateDownloads()
            } catch (e: Exception) {
                logger.e({ "Error handling state changed alert" }, e)
            }
        }

        private fun handleTorrentFinished(alert: TorrentFinishedAlert) {
            try {
                val handle = alert.handle()
                if (handle.isValid) {
                    val hash = handle.infoHash().toHex()
                    val status = handle.status()
                    logger.i {
                        "Torrent finished: hash=$hash, " +
                            "downloaded=${status.totalDone()} bytes, " +
                            "uploaded=${status.totalUpload()} bytes, " +
                            "downloadRate=${status.downloadRate()} bytes/s"
                    }
                    // Persist the final resume state so a completed torrent restores
                    // with piece hashes (SEEDING) instead of re-checking from scratch.
                    try {
                        handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
                    } catch (e: Exception) {
                        logger.w({ "Failed to request final resume data for $hash" }, e)
                    }
                    scheduleImmediateLibrarySync(hash)
                }
                updateDownloads()
            } catch (e: Exception) {
                logger.e({ "Error handling torrent finished alert" }, e)
            }
        }

        private fun scheduleImmediateLibrarySync(torrentHash: String) {
            try {
                val nowMs = System.currentTimeMillis()
                if (
                    !TorrentLibrarySyncTriggerPolicy.shouldTrigger(
                        lastTriggeredAtMs = lastLibrarySyncTriggerAtMs,
                        nowMs = nowMs,
                        cooldownMs = LIBRARY_SYNC_TRIGGER_COOLDOWN_MS,
                    )
                ) {
                    logger.d { "Skip immediate library sync for $torrentHash: cooldown active" }
                    return
                }

                val workRequest =
                    OneTimeWorkRequestBuilder<LibraryScanWorker>()
                        .setConstraints(WorkConstraintsPolicy.libraryScan())
                        .addTag(LibraryScanWorker.WORK_TAG)
                        .addTag("torrent-finished-sync")
                        .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    LibraryScanWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
                lastLibrarySyncTriggerAtMs = nowMs
                logger.i { "Scheduled immediate library sync after torrent finish: hash=$torrentHash" }
            } catch (e: Exception) {
                logger.e({ "Failed to schedule immediate library sync for hash=$torrentHash" }, e)
            }
        }

        private fun handleTorrentError(alert: TorrentErrorAlert) {
            try {
                val handle = alert.handle()
                if (!handle.isValid) {
                    logger.e { "Torrent error alert with invalid handle" }
                    return
                }

                val hash = handle.infoHash().toHex()
                val error = alert.error()
                val status = handle.status()

                logger.e {
                    "Torrent error for $hash: " +
                        "error='${error.message}', " +
                        "state=${status.state()}, " +
                        "progress=${(status.progress() * 100).toInt()}%, " +
                        "downloadRate=${status.downloadRate()} bytes/s, " +
                        "uploadRate=${status.uploadRate()} bytes/s, " +
                        "numPeers=${status.numPeers()}, " +
                        "numSeeds=${status.numSeeds()}"
                }
                updateDownloads()
            } catch (e: Exception) {
                logger.e({ "Error handling torrent error alert" }, e)
            }
        }

        private fun handleMetadataReceived(alert: MetadataReceivedAlert) {
            try {
                val handle = alert.handle()
                if (handle.isValid) {
                    val hash = handle.infoHash().toHex()
                    val torrentInfo = handle.torrentFile()
                    if (torrentInfo != null) {
                        logger.i {
                            "Metadata received for $hash: name='${torrentInfo.name()}', files=${torrentInfo.numFiles()}, size=${torrentInfo.totalSize()} bytes"
                        }
                    } else {
                        logger.i { "Metadata received for $hash (torrent info not yet available)" }
                    }
                }
                updateDownloads()
            } catch (e: Exception) {
                logger.e({ "Error handling metadata received alert" }, e)
            }
        }

        private var lastBlockFinishedUpdateTimeMs: Long = 0L
        private var lastPieceFinishedUpdateTimeMs: Long = 0L

        private fun handleBlockFinished(alert: BlockFinishedAlert) {
            val now = System.currentTimeMillis()
            if (now - lastBlockFinishedUpdateTimeMs < 1000L) return
            lastBlockFinishedUpdateTimeMs = now
            updateDownloads()
        }

        private fun handlePieceFinished(alert: PieceFinishedAlert) {
            try {
                val handle = alert.handle()
                if (handle.isValid) {
                    val hash = handle.infoHash().toHex()
                    val progress = (handle.status().progress() * 100).toInt()
                    val pieceIndex = alert.pieceIndex()
                    logger.d { "Piece finished: hash=$hash, piece=$pieceIndex, progress=$progress%" }
                }
                val now = System.currentTimeMillis()
                if (now - lastPieceFinishedUpdateTimeMs < 2000L) return
                lastPieceFinishedUpdateTimeMs = now
                updateDownloads()
            } catch (e: Exception) {
                logger.e({ "Error handling piece finished alert" }, e)
            }
        }

        private fun handleDhtError(alert: DhtErrorAlert) {
            val error = alert.error()
            logger.w { "DHT error: ${error.message}" }
            // DHT errors are usually non-critical, just log them
        }

        private fun handleStateUpdate(alert: StateUpdateAlert) {
            try {
                val message = alert.message()
                logger.d { "State update: $message" }
                // State updates can be frequent, so we don't update downloads on every one
                // The state changed alert will handle that
            } catch (e: Exception) {
                logger.e({ "Error handling state update alert" }, e)
            }
        }

        // Helper methods

        @Synchronized
        private fun updateDownloads() {
            try {
                val downloads =
                    torrents
                        .mapNotNull { (hash, handle) ->
                            try {
                                // Verify handle is still valid before creating download info
                                if (!handle.isValid) {
                                    logger.w { "Handle invalid for torrent $hash, skipping update" }
                                    null
                                } else {
                                    hash to stateBuilder.buildTorrentDownload(hash, handle, topicIds[hash])
                                }
                            } catch (e: Exception) {
                                logger.e({ "Failed to create download info for torrent $hash" }, e)
                                null
                            }
                        }.toMap()
                _downloadsFlow.value = downloads
            } catch (e: Exception) {
                logger.e({ "Critical error updating downloads" }, e)
                // Don't clear downloads on error, keep last known state
            }
        }

        /**
         * Prioritize specific file
         */
        public fun prioritizeFile(
            hash: String,
            fileIndex: Int,
            priority: Int,
        ) {
            val handle = torrents[hash] ?: return
            try {
                handle.filePriority(fileIndex, org.libtorrent4j.Priority.fromSwig(priority))
                updateDownloads()
            } catch (e: Exception) {
                logger.e(e) { "Failed to prioritize file" }
            }
        }

        /**
         * Set priorities for multiple files
         */
        public fun setFilePriorities(
            hash: String,
            priorities: List<Int>,
        ) {
            val handle = torrents[hash] ?: return
            val torrentInfo = handle.torrentFile() ?: return
            val numFiles = torrentInfo.numFiles()

            if (priorities.size != numFiles) {
                logger.w { "Priority list size mismatch: ${priorities.size} != $numFiles" }
                return
            }

            try {
                // Priority.fromSwig expects int
                val priorityArray = priorities.map { org.libtorrent4j.Priority.fromSwig(it) }.toTypedArray()
                handle.prioritizeFiles(priorityArray)
                updateDownloads()
            } catch (e: Exception) {
                logger.e(e) { "Failed to set file priorities" }
            }
        }

        /**
         * Check if file is ready for streaming (first chunk downloaded)
         * @param bufferSize bytes to check (default 10MB)
         */
        public fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
            bufferSize: Int = 10 * 1024 * 1024, // 10MB
        ): Boolean {
            val handle = torrents[hash] ?: return false
            val torrentInfo = handle.torrentFile() ?: return false
            val fileStorage = torrentInfo.files() ?: return false

            if (fileIndex < 0 || fileIndex >= fileStorage.numFiles()) return false

            val fileSize = fileStorage.fileSize(fileIndex)
            val checkSize = minOf(fileSize, bufferSize.toLong())

            // If file is very small or fully downloaded, it's ready
            val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
            val downloadedBytes = if (fileIndex < progress.size) progress[fileIndex] else 0L

            if (downloadedBytes >= fileSize) return true

            // Check specific pieces
            // We need to map file offset to pieces
            val fileOffset = fileStorage.fileOffset(fileIndex)
            val startPiece = torrentInfo.mapFile(fileIndex, 0, 0).piece()
            // We only check the beginning of the file for "start" capability
            val endOffsetInFile = minOf(fileSize, bufferSize.toLong())
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
        public fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long {
            val handle = torrents[hash] ?: return 0L
            val progress = handle.fileProgress(org.libtorrent4j.swig.file_progress_flags_t())
            return if (fileIndex < progress.size) progress[fileIndex] else 0L
        }

        private fun parseMagnetHash(magnetUri: String): String? =
            try {
                MagnetUriValidationPolicy.extractInfoHash(magnetUri)
            } catch (e: Exception) {
                logger.e({ "Failed to parse magnet hash from: $magnetUri" }, e)
                null
            }
    }
