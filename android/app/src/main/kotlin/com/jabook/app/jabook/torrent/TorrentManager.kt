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

package com.jabook.app.jabook.torrent

import android.content.Context
import android.util.Log
import com.jabook.app.jabook.torrent.data.DownloadProgress
import com.jabook.app.jabook.torrent.data.TorrentState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for torrent downloads using libtorrent4j.
 *
 * Provides sequential download support for audiobook streaming,
 * magnet link handling, and torrent state management.
 */
@Singleton
class TorrentManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "TorrentManager"
            private const val UPDATE_INTERVAL_MS = 1000L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var session: SessionManager? = null
        private val activeTorrents = mutableMapOf<String, TorrentHandle>()
        private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
        val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()
        private var isInitialized = false

        @Synchronized
        fun initialize() {
            if (isInitialized) {
                Log.d(TAG, "Session already initialized")
                return
            }

            Log.d(TAG, "Initializing libtorrent session...")

            try {
                session =
                    SessionManager().apply {
                        addListener(createAlertListener())
                        start()
                    }

                isInitialized = true
                Log.i(TAG, "Libtorrent session initialized successfully")
                startProgressUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize libtorrent session", e)
                isInitialized = false
            }
        }

        private fun createAlertListener(): AlertListener =
            object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.ADD_TORRENT -> handleAddTorrentAlert(alert as AddTorrentAlert)
                        AlertType.TORRENT_FINISHED -> handleTorrentFinished(alert as TorrentFinishedAlert)
                        AlertType.METADATA_RECEIVED -> handleMetadataReceived(alert as MetadataReceivedAlert)
                        AlertType.TORRENT_ERROR -> handleTorrentError(alert as TorrentErrorAlert)
                        else -> {
                            // Other alerts
                        }
                    }
                }
            }

        private fun handleAddTorrentAlert(alert: AddTorrentAlert) {
            val handle = alert.handle()
            val infoHash = handle.infoHash().toHex()
            Log.d(TAG, "Torrent added: $infoHash")

            synchronized(activeTorrents) {
                activeTorrents[infoHash] = handle
                handle.resume()
            }
        }

        private fun handleTorrentFinished(alert: TorrentFinishedAlert) {
            val handle = alert.handle()
            val infoHash = handle.infoHash().toHex()
            Log.i(TAG, "Torrent finished: $infoHash")
        }

        private fun handleMetadataReceived(alert: MetadataReceivedAlert) {
            val handle = alert.handle()
            val infoHash = handle.infoHash().toHex()
            Log.d(TAG, "Metadata received for: $infoHash")
        }

        private fun handleTorrentError(alert: TorrentErrorAlert) {
            val handle = alert.handle()
            val infoHash = handle.infoHash().toHex()
            val error = alert.error()
            Log.e(TAG, "Torrent error: $infoHash - $error")
        }

        private fun startProgressUpdates() {
            scope.launch {
                while (true) {
                    updateProgress()
                    delay(UPDATE_INTERVAL_MS)
                }
            }
        }

        private fun updateProgress() {
            val progressMap = mutableMapOf<String, DownloadProgress>()

            synchronized(activeTorrents) {
                activeTorrents.forEach { (infoHash, handle) ->
                    if (handle.isValid) {
                        val status = handle.status()
                        progressMap[infoHash] = mapTorrentStatus(infoHash, status)
                    }
                }
            }

            _downloads.value = progressMap
        }

        private fun mapTorrentStatus(
            infoHash: String,
            status: TorrentStatus,
        ): DownloadProgress =
            DownloadProgress(
                infoHash = infoHash,
                percentage = status.progress() * 100f,
                downloadRate = status.downloadRate().toLong(),
                uploadRate = status.uploadRate().toLong(),
                downloaded = status.totalDone(),
                uploaded = status.totalUpload(),
                totalSize = status.totalWanted(),
                numPeers = status.numPeers(),
                numSeeds = status.numSeeds(),
                state = mapTorrentState(status.state()),
            )

        private fun mapTorrentState(state: TorrentStatus.State): TorrentState =
            when (state) {
                TorrentStatus.State.CHECKING_FILES -> TorrentState.CHECKING
                TorrentStatus.State.DOWNLOADING_METADATA -> TorrentState.DOWNLOADING_METADATA
                TorrentStatus.State.DOWNLOADING -> TorrentState.DOWNLOADING
                TorrentStatus.State.FINISHED -> TorrentState.FINISHED
                TorrentStatus.State.SEEDING -> TorrentState.SEEDING
                TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentState.CHECKING
                else -> TorrentState.QUEUED
            }

        suspend fun addMagnetLink(
            magnetUri: String,
            savePath: String,
            sequential: Boolean = true,
        ): String {
            if (!isInitialized) {
                initialize()
            }

            val session = this.session ?: throw IllegalStateException("Session not initialized")

            Log.d(TAG, "Adding magnet link: $magnetUri")
            Log.d(TAG, "Save path: $savePath")

            val saveDir = File(savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }

            // Fetch magnet metadata (20 second timeout)
            val metadata = session.fetchMagnet(magnetUri, 20, saveDir)
            if (metadata == null || metadata.isEmpty()) {
                throw IllegalStateException("Failed to fetch magnet metadata")
            }

            // Parse torrent info from metadata
            val ti = TorrentInfo.bdecode(metadata)

            // Download torrent using correct API signature
            // download(torrentInfo, saveDir, resumeData, priorities, filePriorities, flags)
            val handle =
                if (sequential) {
                    // Sequential download with SEQUENTIAL_DOWNLOAD flag
                    session.download(ti, saveDir, null, null, null, TorrentFlags.SEQUENTIAL_DOWNLOAD)
                } else {
                    // Normal download
                    session.download(ti, saveDir)
                }

            val infoHash = ti.infoHash().toHex()

            // Alert handler will add to activeTorrents and resume when ADD_TORRENT alert fires
            // synchronized(activeTorrents) {
            //     activeTorrents[infoHash] = handle
            //     handle.resume()
            // }

            Log.i(TAG, "Torrent added successfully: $infoHash")

            return infoHash
        }

        suspend fun pauseDownload(infoHash: String) {
            synchronized(activeTorrents) {
                val handle = activeTorrents[infoHash]
                handle?.pause()
                Log.d(TAG, "Paused torrent: $infoHash")
            }
        }

        suspend fun resumeDownload(infoHash: String) {
            synchronized(activeTorrents) {
                val handle = activeTorrents[infoHash]
                handle?.resume()
                Log.d(TAG, "Resumed torrent: $infoHash")
            }
        }

        suspend fun removeDownload(
            infoHash: String,
            deleteFiles: Boolean = false,
        ) {
            val session = this.session ?: return

            synchronized(activeTorrents) {
                val handle = activeTorrents.remove(infoHash)
                if (handle != null) {
                    session.remove(handle)
                    Log.d(TAG, "Removed torrent: $infoHash (deleteFiles=$deleteFiles)")
                }
            }

            updateProgress()
        }

        fun getDownloadProgress(infoHash: String): Flow<DownloadProgress> =
            flow {
                _downloads.collect { progressMap ->
                    progressMap[infoHash]?.let { emit(it) }
                }
            }

        fun shutdown() {
            Log.d(TAG, "Shutting down libtorrent session...")

            session?.stop()
            session = null
            isInitialized = false

            synchronized(activeTorrents) {
                activeTorrents.clear()
            }

            _downloads.value = emptyMap()

            Log.i(TAG, "Libtorrent session shut down")
        }
    }
