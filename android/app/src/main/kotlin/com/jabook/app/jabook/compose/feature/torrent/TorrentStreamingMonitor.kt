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

package com.jabook.app.jabook.compose.feature.torrent

import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors playback position vs downloaded content for torrent streaming.
 * Pauses playback if buffering is needed and resumes when ready.
 */
@Singleton
class TorrentStreamingMonitor
    @Inject
    constructor(
        private val torrentManager: TorrentManager,
    ) {
        private val _isBuffering = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isBuffering = _isBuffering.asStateFlow()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var monitoringJob: Job? = null

        private var currentHash: String? = null
        private var currentFileIndex: Int = -1

        private var isPausedForBuffering = false
            set(value) {
                field = value
                _isBuffering.value = value
            }

        companion object {
            // Configuration
            private const val BUFFER_LOW_THRESHOLD_BYTES = 1 * 1024 * 1024L // 1MB
            private const val BUFFER_RESUME_THRESHOLD_BYTES = 5 * 1024 * 1024L // 5MB
            private const val POLLING_INTERVAL_MS = 1000L
        }

        fun startMonitoring(
            hash: String,
            fileIndex: Int,
        ) {
            stopMonitoring()
            currentHash = hash
            currentFileIndex = fileIndex
            isPausedForBuffering = false

            monitoringJob =
                scope.launch {
                    while (isActive) {
                        checkBufferState()
                        delay(POLLING_INTERVAL_MS)
                    }
                }
        }

        fun stopMonitoring() {
            monitoringJob?.cancel()
            monitoringJob = null
            currentHash = null
            currentFileIndex = -1
            isPausedForBuffering = false
        }

        private fun checkBufferState() {
            val hash = currentHash ?: return
            val fileIndex = currentFileIndex
            if (fileIndex < 0) return

            val service = AudioPlayerService.getInstance() ?: return
            if (!service.isFullyInitialized()) return

            val player = service.mediaSession?.player ?: return
            val currentDuration = player.duration
            val currentPosition = player.currentPosition

            // Only if we are playing the file we think we are monitoring?
            // Ideally check metadata or path, but simplified for now:
            if (currentDuration <= 0) return // Not playing or unknown

            val download = torrentManager.getDownload(hash) ?: return
            val torrentFile = download.files.find { it.index == fileIndex } ?: return

            val totalBytes = torrentFile.size

            // Precise bytes
            val downloadedBytes = torrentManager.getDownloadedBytes(hash, fileIndex)

            // Calculate estimated byte position of player: (position / duration) * totalBytes
            val playedBytes = (currentPosition.toDouble() / currentDuration.toDouble() * totalBytes).toLong()

            val availableBytesAhead = downloadedBytes - playedBytes

            val isPlaying = player.isPlaying

            if (isPlaying) {
                // If we are playing, and buffer gets low, pause and mark as buffering
                if (availableBytesAhead < BUFFER_LOW_THRESHOLD_BYTES && downloadedBytes < totalBytes) {
                    android.util.Log.i("TorrentMonitor", "Buffering... Available: $availableBytesAhead")
                    service.pause()
                    isPausedForBuffering = true
                    // Buffering state is tracked via isPausedForBuffering
                }
            } else if (isPausedForBuffering) {
                // If we are paused due to buffering, check if we have enough to resume
                if (availableBytesAhead > BUFFER_RESUME_THRESHOLD_BYTES || downloadedBytes >= totalBytes) {
                    android.util.Log.i("TorrentMonitor", "Buffering clear. Resuming. Available: $availableBytesAhead")
                    service.play()
                    isPausedForBuffering = false
                }
            } else {
                // User paused manually, do not auto resume
            }
        }
    }
