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

package com.jabook.app.jabook.compose.data.repository

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.MediaControllerExtensions
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SleepTimerRepository using AudioPlayerService as source of truth.
 *
 * Polls the service to keep UI state in sync.
 */
@Singleton
public class SleepTimerRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val loggerFactory: LoggerFactory,
    ) : SleepTimerRepository {
        private val logger = loggerFactory.get("SleepTimerRepository")
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        private val _timerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
        override val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

        // MediaController for accessing service through custom commands
        private var mediaController: MediaController? = null
        private var mediaControllerFuture: ListenableFuture<MediaController>? = null

        init {
            // Initialize MediaController for service access
            initMediaController()

            // Poll service for timer state with adaptive polling interval
            // Poll more frequently when timer is active, less when idle
            scope.launch {
                var lastState: SleepTimerState = SleepTimerState.Idle
                while (isActive) {
                    val newState = updateTimerState() // This is now suspend
                    // Adaptive polling: faster when active, slower when idle
                    val delayMs =
                        when {
                            newState is SleepTimerState.Active -> 1000L // 1 second when active
                            newState is SleepTimerState.EndOfChapter -> 1000L // 1 second for end of chapter
                            newState is SleepTimerState.EndOfTrack -> 1000L // 1 second for end of track
                            // Immediate check when transitioning to idle
                            lastState !is SleepTimerState.Idle && newState is SleepTimerState.Idle -> 1000L
                            else -> 5000L // 5 seconds when idle
                        }
                    lastState = newState
                    delay(delayMs)
                }
            }
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
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            logger.e({ "MediaController initialization interrupted" }, e)
                        } catch (e: TimeoutException) {
                            logger.e({ "Timed out while initializing MediaController" }, e)
                        } catch (e: ExecutionException) {
                            logger.e({ "Failed to initialize MediaController" }, e)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            } catch (e: CancellationException) {
                throw e
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
         * Updates timer state from service via MediaController.
         * Returns the new state for adaptive polling.
         */
        private suspend fun updateTimerState(): SleepTimerState {
            val controller = mediaController
            if (controller == null) {
                // Try to reinitialize if not available
                if (mediaControllerFuture == null) {
                    initMediaController()
                }
                if (_timerState.value !is SleepTimerState.Idle) {
                    _timerState.value = SleepTimerState.Idle
                }
                return SleepTimerState.Idle
            }

            // Use MediaController custom commands to get timer state
            val newState =
                try {
                    val isEndOfTrack = MediaControllerExtensions.isSleepTimerEndOfTrack(controller)
                    if (isEndOfTrack) {
                        SleepTimerState.EndOfTrack()
                    } else {
                        val isEndOfChapter = MediaControllerExtensions.isSleepTimerEndOfChapter(controller)
                        if (isEndOfChapter) {
                            SleepTimerState.EndOfChapter
                        } else {
                            val remaining = MediaControllerExtensions.getSleepTimerRemainingSeconds(controller)
                            if (remaining != null && remaining > 0) {
                                SleepTimerState.Active(remaining)
                            } else {
                                SleepTimerState.Idle
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Failed to get timer state via MediaController" }, e)
                    SleepTimerState.Idle
                }

            // Only update if state actually changed to avoid unnecessary recompositions
            if (_timerState.value != newState) {
                _timerState.value = newState
            }

            return newState
        }

        override fun startTimer(durationMinutes: Int) {
            // Use MediaController custom command for setSleepTimerMinutes
            scope.launch {
                val controller = mediaController
                if (controller != null) {
                    try {
                        val future = MediaControllerExtensions.setSleepTimerMinutes(controller, durationMinutes)
                        val result =
                            future.get(
                                com.jabook.app.jabook.audio.MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS
                                    .toLong(),
                                TimeUnit.SECONDS,
                            )
                        if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                            // State will be updated by polling, but eagerly update for responsiveness
                            _timerState.value = SleepTimerState.Active(durationMinutes * 60)
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw CancellationException("Interrupted while setting sleep timer").apply { initCause(e) }
                    } catch (e: TimeoutException) {
                        logger.e({ "Timed out while setting sleep timer" }, e)
                    } catch (e: ExecutionException) {
                        logger.e({ "Failed to set sleep timer" }, e)
                    }
                } else {
                    logger.w { "MediaController not available for startTimer" }
                }
            }
        }

        override fun startTimerEndOfChapter() {
            // Use MediaController custom command for setSleepTimerEndOfChapter
            scope.launch {
                val controller = mediaController
                if (controller != null) {
                    try {
                        val future = MediaControllerExtensions.setSleepTimerEndOfChapter(controller)
                        val result =
                            future.get(
                                com.jabook.app.jabook.audio.MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS
                                    .toLong(),
                                TimeUnit.SECONDS,
                            )
                        if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                            val fallbackToTrackEnd =
                                result.extras.getBoolean(
                                    com.jabook.app.jabook.audio.AudioPlayerLibrarySessionCallback
                                        .ARG_RESULT_FALLBACK_TO_TRACK_END,
                                    false,
                                )
                            _timerState.value =
                                if (fallbackToTrackEnd) {
                                    SleepTimerState.EndOfTrack(fallbackFromChapter = true)
                                } else {
                                    SleepTimerState.EndOfChapter
                                }
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw CancellationException("Interrupted while setting sleep timer end of chapter").apply {
                            initCause(e)
                        }
                    } catch (e: TimeoutException) {
                        logger.e({ "Timed out while setting sleep timer end of chapter" }, e)
                    } catch (e: ExecutionException) {
                        logger.e({ "Failed to set sleep timer end of chapter" }, e)
                    }
                } else {
                    logger.w { "MediaController not available for startTimerEndOfChapter" }
                }
            }
        }

        override fun startTimerEndOfTrack() {
            scope.launch {
                val controller = mediaController
                if (controller != null) {
                    try {
                        val future = MediaControllerExtensions.setSleepTimerEndOfTrack(controller)
                        val result =
                            future.get(
                                com.jabook.app.jabook.audio.MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS
                                    .toLong(),
                                TimeUnit.SECONDS,
                            )
                        if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                            _timerState.value = SleepTimerState.EndOfTrack()
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw CancellationException("Interrupted while setting sleep timer end of track").apply { initCause(e) }
                    } catch (e: TimeoutException) {
                        logger.e({ "Timed out while setting sleep timer end of track" }, e)
                    } catch (e: ExecutionException) {
                        logger.e({ "Failed to set sleep timer end of track" }, e)
                    }
                } else {
                    logger.w { "MediaController not available for startTimerEndOfTrack" }
                }
            }
        }

        override fun cancelTimer() {
            // Use MediaController custom command for cancelSleepTimer
            scope.launch {
                val controller = mediaController
                if (controller != null) {
                    try {
                        val future = MediaControllerExtensions.cancelSleepTimer(controller)
                        val result =
                            future.get(
                                com.jabook.app.jabook.audio.MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS
                                    .toLong(),
                                TimeUnit.SECONDS,
                            )
                        if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                            _timerState.value = SleepTimerState.Idle
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw CancellationException("Interrupted while cancelling sleep timer").apply { initCause(e) }
                    } catch (e: TimeoutException) {
                        logger.e({ "Timed out while cancelling sleep timer" }, e)
                    } catch (e: ExecutionException) {
                        logger.e({ "Failed to cancel sleep timer" }, e)
                    }
                } else {
                    logger.w { "MediaController not available for cancelTimer" }
                }
            }
        }
    }
