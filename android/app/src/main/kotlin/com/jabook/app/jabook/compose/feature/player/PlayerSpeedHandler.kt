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

package com.jabook.app.jabook.compose.feature.player

import com.jabook.app.jabook.audio.HoldToBoostPolicy
import com.jabook.app.jabook.audio.processors.SpeedMemoryHierarchy
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.util.runCatchingCancelable
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val DEFAULT_HOLD_TO_BOOST_SPEED: Float = 2.5f

private fun resolveHoldToBoostSpeed(configuredSpeed: Float): Float =
    when (configuredSpeed) {
        2.0f,
        2.5f,
        3.0f,
        -> configuredSpeed
        else -> DEFAULT_HOLD_TO_BOOST_SPEED
    }

/**
 * Playback speed application (controller + global + per-book persistence) and hold-to-boost.
 *
 * @param bookId Current book identifier
 * @param playerController Controller for applying speed to the player
 * @param settingsRepository DataStore-backed settings (hold-to-boost speed)
 * @param userPreferencesRepository Repository for the global speed preference
 * @param booksRepository Repository for per-book speed memory
 * @param uiState Current player UI state
 * @param viewModelScope Coroutine scope for async operations
 * @param loggerFactory Logger factory
 * @param dispatchIntent Callback for dispatching intents (speed changes, error reports)
 */
internal class PlayerSpeedHandler(
    private val bookId: String,
    private val playerController: AudioPlayerController,
    private val settingsRepository: ProtoSettingsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val booksRepository: BooksRepository,
    private val uiState: StateFlow<PlayerState>,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
    private val dispatchIntent: (PlayerIntent) -> Unit,
) {
    private val logger: Logger = loggerFactory.get("PlayerSpeedHandler")
    private var holdToBoostPolicy = HoldToBoostPolicy(boostSpeed = DEFAULT_HOLD_TO_BOOST_SPEED)

    fun applyPlaybackSpeed(
        speed: Float,
        rememberForBook: Boolean,
    ) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.5f)
        viewModelScope.launch {
            runCatchingCancelable { playerController.setPlaybackSpeed(clampedSpeed) }
                .onFailure { error ->
                    logger.e({ "Failed to set playback speed on player" }, error)
                    dispatchIntent(PlayerIntent.ReportError("Failed to update playback speed"))
                }
        }
        if (!rememberForBook) return
        viewModelScope.launch {
            runCatchingCancelable {
                val activeState = uiState.value as? PlayerState.Active
                val listenedMs = playerController.currentPosition.value
                if (
                    SpeedMemoryHierarchy.shouldRecordBookSpeed(
                        listenedMs = listenedMs,
                        previousSpeed = null,
                        newSpeed = clampedSpeed,
                    )
                ) {
                    booksRepository.updatePreferredPlaybackSpeed(bookId = bookId, speed = clampedSpeed)
                }
            }.onFailure { error ->
                logger.w(error) { "Failed to persist per-book playback speed preference" }
            }
        }
        viewModelScope.launch {
            runCatchingCancelable { userPreferencesRepository.setPlaybackSpeed(clampedSpeed) }
                .onFailure { error ->
                    logger.e({ "Failed to persist playback speed" }, error)
                    dispatchIntent(PlayerIntent.ReportError("Failed to save playback speed"))
                }
        }
    }

    fun startHoldToBoost(currentPlaybackSpeed: Float) {
        val boostedSpeed = holdToBoostPolicy.onPress(currentPlaybackSpeed)
        dispatchIntent(PlayerIntent.SetPlaybackSpeed(boostedSpeed))
    }

    fun endHoldToBoost() {
        val restoreSpeed = holdToBoostPolicy.onRelease() ?: return
        dispatchIntent(PlayerIntent.SetPlaybackSpeed(restoreSpeed))
    }

    fun observeHoldToBoostSpeedSetting() {
        viewModelScope.launch {
            settingsRepository.userPreferences
                .map { it.holdToBoostSpeed }
                .distinctUntilChanged()
                .collect { configuredSpeed ->
                    holdToBoostPolicy = HoldToBoostPolicy(boostSpeed = resolveHoldToBoostSpeed(configuredSpeed))
                }
        }
    }
}
