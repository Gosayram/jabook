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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
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
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("TorrentMonitor")
        private val _isBuffering = kotlinx.coroutines.flow.MutableStateFlow(false)
        public val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

        // MediaController is built with the main looper — polling it from any
        // other dispatcher throws IllegalStateException. Keep the loop on Main.
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("TorrentStreamingMonitor"),
            )
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

            // Auto-stop polling when the player is genuinely stopped (STATE_IDLE/STATE_ENDED)
            // for this long — prevents endless 1s wake-ups after playback ends.
            private const val STOPPED_GRACE_PERIOD_MS = 5 * 60_000L
            private const val STOPPED_GRACE_TICKS = STOPPED_GRACE_PERIOD_MS / POLLING_INTERVAL_MS

            // Auto-stop when the player stays paused without monitor-managed buffering
            // (e.g. user pause) for this many consecutive ticks.
            private const val PAUSED_STOP_TICKS = 30L

            // Tolerate the player being on another item briefly (stream startup swaps
            // items in after startMonitoring); stop the monitor when it persists.
            private const val FOREIGN_ITEM_STOP_TICKS = 30L
        }

        // Consecutive polls where the player was truly stopped (not paused)
        private var stoppedTickCount = 0L

        // Consecutive polls where the player was paused (not monitor-managed buffering)
        private var pausedTickCount = 0L

        // Consecutive polls where the player played a different item than the monitored one
        private var foreignItemTickCount = 0L

        public fun startMonitoring(
            hash: String,
            fileIndex: Int,
        ) {
            stopMonitoring()
            currentHash = hash
            currentFileIndex = fileIndex
            isPausedForBuffering = false
            pausedByUser = false
            stoppedTickCount = 0L
            pausedTickCount = 0L
            foreignItemTickCount = 0L

            // Initialize MediaController for service access
            initMediaController()

            monitoringJob =
                scope.launch {
                    while (isActive) {
                        try {
                            if (checkBufferState()) {
                                break
                            }
                        } catch (e: Exception) {
                            logger.e({ "Buffer state check failed: ${e.message}" }, e)
                        }
                        delay(POLLING_INTERVAL_MS)
                    }
                    // Clean up state (buffering flag, counters, controller) on auto-stop
                    stopMonitoring()
                }
        }

        public fun stopMonitoring() {
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
        public fun onUserPaused() {
            pausedByUser = true
            isPausedForBuffering = false
        }

        /**
         * Call this when user manually resumes playback.
         * Allows monitor to resume control.
         */
        public fun onUserResumed() {
            pausedByUser = false
        }

        private fun initMediaController() {
            try {
                val sessionToken =
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
                            val controller =
                                mediaControllerFuture?.get(
                                    com.jabook.app.jabook.audio.MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS
                                        .toLong(),
                                    TimeUnit.SECONDS,
                                )
                            mediaController = controller
                            logger.d { "MediaController initialized" }
                        } catch (e: Exception) {
                            logger.e({ "Failed to initialize MediaController" }, e)
                            // Clear the failed future so the next poll tick retries
                            // instead of waking forever with a null controller.
                            mediaControllerFuture?.let { MediaController.releaseFuture(it) }
                            mediaControllerFuture = null
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            } catch (e: Exception) {
                logger.e({ "Failed to create MediaController" }, e)
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

        /**
         * Checks buffer state.
         * @return true when the monitor should auto-stop (player stopped beyond grace period).
         */
        private fun checkBufferState(): Boolean {
            val hash = currentHash ?: return false
            val fileIndex = currentFileIndex
            if (fileIndex < 0) return false

            // Use MediaController instead of getInstance()
            val controller =
                mediaController ?: run {
                    // Try to reinitialize if not available
                    if (mediaControllerFuture == null) {
                        initMediaController()
                    }
                    return false
                }

            // Player genuinely stopped (not paused) — track grace period, then auto-stop
            val playbackState = controller.playbackState
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                stoppedTickCount++
                if (stoppedTickCount >= STOPPED_GRACE_TICKS) {
                    logger.i { "Player stopped for $STOPPED_GRACE_PERIOD_MS ms — stopping stream monitor" }
                    return true
                }
                return false
            }
            stoppedTickCount = 0L

            val currentDuration = controller.duration
            val currentPosition = controller.currentPosition

            if (currentDuration <= 0) return false // Not playing or unknown

            val download = torrentManager.getDownload(hash) ?: return false
            val torrentFile = download.files.find { it.index == fileIndex } ?: return false

            // Only manage playback of the monitored torrent file — pausing or resuming
            // an unrelated item (user started another book) would be wrong. The URI is
            // built the same way as in TorrentDetailsViewModel.playFile.
            val monitoredPath = File(download.savePath, torrentFile.path).absolutePath
            val currentItemPath =
                controller.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.path
            if (currentItemPath != null && currentItemPath != monitoredPath) {
                foreignItemTickCount++
                if (foreignItemTickCount >= FOREIGN_ITEM_STOP_TICKS) {
                    logger.i { "Player is on another item ($currentItemPath) — stopping stream monitor" }
                    return true
                }
                return false
            }
            foreignItemTickCount = 0L

            // Paused (playWhenReady=false) without monitor-managed buffering, e.g. user
            // pause — stop after a bounded number of ticks instead of polling forever.
            if (!controller.playWhenReady && !isPausedForBuffering) {
                pausedTickCount++
                if (pausedTickCount >= PAUSED_STOP_TICKS) {
                    logger.i { "Player paused (not buffering) for $PAUSED_STOP_TICKS ticks — stopping stream monitor" }
                    return true
                }
                return false
            }
            pausedTickCount = 0L

            val totalBytes = torrentFile.size

            // Precise bytes
            val downloadedBytes = torrentManager.getDownloadedBytes(hash, fileIndex)

            // Calculate estimated byte position of player: (position / duration) * totalBytes
            val playedBytes = (currentPosition.toDouble() / currentDuration.toDouble() * totalBytes).toLong()

            val availableBytesAhead = downloadedBytes - playedBytes

            val isPlaying = controller.isPlaying

            if (isPlaying) {
                // If we are playing, and buffer gets low, pause and mark as buffering
                if (availableBytesAhead < BUFFER_LOW_THRESHOLD_BYTES && downloadedBytes < totalBytes) {
                    logger.i { "Buffering... Available: $availableBytesAhead" }
                    controller.pause()
                    isPausedForBuffering = true
                    pausedByUser = false // Monitor paused, not user
                }
            } else if (isPausedForBuffering && !pausedByUser) {
                // If we are paused due to buffering (and not by user), check if we have enough to resume
                if (availableBytesAhead > BUFFER_RESUME_THRESHOLD_BYTES || downloadedBytes >= totalBytes) {
                    logger.i { "Buffering clear. Resuming. Available: $availableBytesAhead" }
                    controller.play()
                    isPausedForBuffering = false
                }
            } else if (pausedByUser) {
                // User paused manually, do not auto resume
                // Reset buffering flag if user manually paused
                isPausedForBuffering = false
            }
            return false
        }
    }
