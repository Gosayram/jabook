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

package com.jabook.app.jabook.compose.feature.torrent

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors playback position vs downloaded content for torrent streaming.
 * Pauses playback if buffering is needed and resumes when ready.
 */
@Singleton
public class TorrentStreamingMonitor
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val torrentManager: TorrentManager,
    ) {
        private val _isBuffering = kotlinx.coroutines.flow.MutableStateFlow(false)
        public val isBuffering = _isBuffering.asStateFlow()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var monitoringJob: Job? = null

        private var currentHash: String? = null
        private var currentFileIndex: Int = -1

        private var mediaController: MediaController? = null
        private var mediaControllerFuture: ListenableFuture<MediaController>? = null

        // Track who paused playback: user or monitor
        private var pausedByUser = false
        private var isPausedForBuffering = false
            set(value) {
                field = value
                _isBuffering.value = value
            }

        public companion object {
            // Configuration
            private const val BUFFER_LOW_THRESHOLD_BYTES = 1 * 1024 * 1024L // 1MB
            private const val BUFFER_RESUME_THRESHOLD_BYTES = 5 * 1024 * 1024L // 5MB
            private const val POLLING_INTERVAL_MS = 1000L
        }

        public fun startMonitoring(
            hash: String,
            fileIndex: Int,
        ) {
            stopMonitoring()
            currentHash = hash
            currentFileIndex = fileIndex
            isPausedForBuffering = false
            pausedByUser = false

            // Initialize MediaController for service access
            initMediaController()

            monitoringJob =
                scope.launch {
                    while (isActive) {
                        checkBufferState()
                        delay(POLLING_INTERVAL_MS)
                    }
                }
        }

        public fun stopMonitoring() : Unit {
            monitoringJob?.cancel()
            monitoringJob = null
            currentHash = null
            currentFileIndex = -1
            isPausedForBuffering = false
            pausedByUser = false
            releaseMediaController()
        }

        /**
         * Call this when user manually pauses playback.
         * Prevents monitor from auto-resuming.
         */
        public fun onUserPaused() : Unit {
            pausedByUser = true
            isPausedForBuffering = false
        }

        /**
         * Call this when user manually resumes playback.
         * Allows monitor to resume control.
         */
        public fun onUserResumed() : Unit {
            pausedByUser = false
        }

        private fun initMediaController() {
            try {
                public val sessionToken =
                    SessionToken(
                        context,
                        ComponentName(context, AudioPlayerService::class.java),
                    )

                mediaControllerFuture =
                    MediaController
                        .Builder(context, sessionToken)
                        .setApplicationLooper(context.mainLooper)
                        .buildAsync()

                mediaControllerFuture?.addListener(
                    {
                        try {
                            public val controller =
                                mediaControllerFuture?.get(
                                    com.jabook.app.jabook.audio.MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS,
                                    TimeUnit.SECONDS,
                                )
                            mediaController = controller
                            android.util.Log.d("TorrentMonitor", "MediaController initialized")
                        } catch (e: Exception) {
                            android.util.Log.w("TorrentMonitor", "Failed to initialize MediaController", e)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            } catch (e: Exception) {
                android.util.Log.w("TorrentMonitor", "Failed to create MediaController", e)
            }
        }

        private fun releaseMediaController() {
            mediaController?.release()
            mediaController = null
            mediaControllerFuture?.let {
                MediaController.releaseFuture(it)
            }
            mediaControllerFuture = null
        }

        private fun checkBufferState() {
            public val hash = currentHash ?: return
            public val fileIndex = currentFileIndex
            if (fileIndex < 0) return

            // Use MediaController instead of getInstance()
            public val controller =
                mediaController ?: run {
                    // Try to reinitialize if not available
                    if (mediaControllerFuture == null) {
                        initMediaController()
                    }
                    return
                }

            public val currentDuration = controller.duration
            public val currentPosition = controller.currentPosition

            // Only if we are playing the file we think we are monitoring?
            // Ideally check metadata or path, but simplified for now:
            if (currentDuration <= 0) return // Not playing or unknown

            public val download = torrentManager.getDownload(hash) ?: return
            public val torrentFile = download.files.find { it.index == fileIndex } ?: return

            public val totalBytes = torrentFile.size

            // Precise bytes
            public val downloadedBytes = torrentManager.getDownloadedBytes(hash, fileIndex)

            // Calculate estimated byte position of player: (position / duration) * totalBytes
            public val playedBytes = (currentPosition.toDouble() / currentDuration.toDouble() * totalBytes).toLong()

            public val availableBytesAhead = downloadedBytes - playedBytes

            public val isPlaying = controller.isPlaying

            if (isPlaying) {
                // If we are playing, and buffer gets low, pause and mark as buffering
                if (availableBytesAhead < BUFFER_LOW_THRESHOLD_BYTES && downloadedBytes < totalBytes) {
                    android.util.Log.i("TorrentMonitor", "Buffering... Available: $availableBytesAhead")
                    controller.pause()
                    isPausedForBuffering = true
                    pausedByUser = false // Monitor paused, not user
                }
            } else if (isPausedForBuffering && !pausedByUser) {
                // If we are paused due to buffering (and not by user), check if we have enough to resume
                if (availableBytesAhead > BUFFER_RESUME_THRESHOLD_BYTES || downloadedBytes >= totalBytes) {
                    android.util.Log.i("TorrentMonitor", "Buffering clear. Resuming. Available: $availableBytesAhead")
                    controller.play()
                    isPausedForBuffering = false
                }
            } else if (pausedByUser) {
                // User paused manually, do not auto resume
                // Reset buffering flag if user manually paused
                isPausedForBuffering = false
            }
        }
    }
