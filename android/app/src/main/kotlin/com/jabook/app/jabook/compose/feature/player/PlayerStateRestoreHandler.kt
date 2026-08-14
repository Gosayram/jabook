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

import androidx.lifecycle.SavedStateHandle
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.PlayerStateSnapshotPreference
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.repository.SleepTimerRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import com.jabook.app.jabook.compose.domain.model.toTypedResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.jabook.app.jabook.compose.domain.model.Result as TypedResult

private const val STATE_SNAPSHOT_BOOK_ID: String = "player_snapshot.book_id"
private const val STATE_SNAPSHOT_POSITION_MS: String = "player_snapshot.position_ms"
private const val STATE_SNAPSHOT_CHAPTER_INDEX: String = "player_snapshot.chapter_index"
private const val STATE_SNAPSHOT_PLAYBACK_SPEED: String = "player_snapshot.playback_speed"
private const val STATE_SNAPSHOT_SLEEP_MODE: String = "player_snapshot.sleep_mode"

/**
 * Restores and persists playback bootstrap state (position, chapter, speed, sleep mode)
 * across process death and app restarts.
 *
 * @param bookId Current book identifier
 * @param savedStateHandle SavedStateHandle for process-death snapshots
 * @param settingsRepository DataStore-backed settings repository
 * @param userPreferencesRepository User preferences repository
 * @param sleepTimerRepository Sleep timer repository
 * @param playbackPositionRepository Playback position repository
 * @param sleepTimerState Current sleep timer state
 * @param uiState Current player UI state
 * @param restoredBootstrapSnapshot Shared bootstrap snapshot state
 * @param isPlaybackRestoreReady Shared restore-ready flag
 * @param viewModelScope Coroutine scope for collectors
 * @param loggerFactory Logger factory
 */
internal class PlayerStateRestoreHandler(
    private val bookId: String,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: ProtoSettingsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sleepTimerRepository: SleepTimerRepository,
    private val playbackPositionRepository: PlaybackPositionRepository,
    private val sleepTimerState: StateFlow<SleepTimerState>,
    private val uiState: StateFlow<PlayerState>,
    private val restoredBootstrapSnapshot: MutableStateFlow<RestoredBootstrapSnapshot?>,
    private val isPlaybackRestoreReady: MutableStateFlow<Boolean>,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
) {
    private val logger: Logger = loggerFactory.get("PlayerStateRestoreHandler")
    private var lastPersistedPlayerSnapshot: PlayerStateSnapshot? = null

    /** Restores the process-death snapshot from SavedStateHandle synchronously. */
    fun restoreFromSavedState() {
        val snapshotBookId: String = savedStateHandle[STATE_SNAPSHOT_BOOK_ID] ?: return
        if (snapshotBookId != bookId) return

        val restoredPosition = (savedStateHandle[STATE_SNAPSHOT_POSITION_MS] ?: 0L).coerceAtLeast(0L)
        val restoredChapterIndex = (savedStateHandle[STATE_SNAPSHOT_CHAPTER_INDEX] ?: 0).coerceAtLeast(0)
        val restoredSpeed = (savedStateHandle[STATE_SNAPSHOT_PLAYBACK_SPEED] ?: 1.0f).coerceAtLeast(0f)
        val restoredSleepMode = savedStateHandle[STATE_SNAPSHOT_SLEEP_MODE] ?: PlayerStateSnapshotPolicy.MODE_IDLE
        restoredBootstrapSnapshot.value =
            RestoredBootstrapSnapshot(
                positionMs = restoredPosition,
                chapterIndex = restoredChapterIndex,
                playbackSpeed = restoredSpeed,
                sleepTimerMode = restoredSleepMode,
                hasRestoredSpeed = restoredSpeed > 0f,
            )

        logger.d {
            "Restored player snapshot: chapter=$restoredChapterIndex, " +
                "position=${restoredPosition}ms, speed=$restoredSpeed, sleepMode=$restoredSleepMode"
        }
    }

    /** Restores the persisted snapshot from DataStore when SavedStateHandle had none. */
    fun restoreFromDataStore() {
        viewModelScope.launch {
            val existingSnapshot = restoredBootstrapSnapshot.value
            if (existingSnapshot != null) return@launch
            val snapshot = settingsRepository.playerStateSnapshot.first() ?: return@launch
            val restoredWhileReading = restoredBootstrapSnapshot.value
            if (restoredWhileReading != null) {
                return@launch
            }
            if (snapshot.bookId != bookId) return@launch
            val restoredPosition = snapshot.positionMs.coerceAtLeast(0L)
            val restoredChapterIndex = snapshot.chapterIndex.coerceAtLeast(0)
            val restoredSpeed = snapshot.playbackSpeed.coerceAtLeast(0f)
            val restoredSleepMode = snapshot.sleepTimerMode.ifBlank { PlayerStateSnapshotPolicy.MODE_IDLE }
            restoredBootstrapSnapshot.value =
                RestoredBootstrapSnapshot(
                    positionMs = restoredPosition,
                    chapterIndex = restoredChapterIndex,
                    playbackSpeed = restoredSpeed,
                    sleepTimerMode = restoredSleepMode,
                    hasRestoredSpeed = restoredSpeed > 0f,
                )
            restorePlaybackSpeedFromSnapshotIfNeeded()
            restoreSleepTimerModeFromSnapshotIfNeeded()
            logger.d {
                "Restored player snapshot from DataStore: chapter=$restoredChapterIndex, " +
                    "position=${restoredPosition}ms, speed=$restoredSpeed, sleepMode=$restoredSleepMode"
            }
        }
    }

    fun restorePlaybackSpeedFromSnapshotIfNeeded() {
        viewModelScope.launch {
            val bootstrapSnapshot = restoredBootstrapSnapshot.value ?: return@launch
            if (bootstrapSnapshot.playbackSpeed <= 0f) return@launch
            runCatching {
                val currentSpeed = userPreferencesRepository.userData.first().playbackSpeed
                if (kotlin.math.abs(currentSpeed - bootstrapSnapshot.playbackSpeed) > 0.01f) {
                    userPreferencesRepository.setPlaybackSpeed(bootstrapSnapshot.playbackSpeed)
                }
            }.onFailure { error ->
                logger.w(error) { "Failed to restore playback speed from player snapshot" }
            }
        }
    }

    fun restoreSleepTimerModeFromSnapshotIfNeeded() {
        viewModelScope.launch {
            val bootstrapSnapshot = restoredBootstrapSnapshot.value ?: return@launch
            when (bootstrapSnapshot.sleepTimerMode) {
                PlayerStateSnapshotPolicy.MODE_END_OF_CHAPTER -> {
                    if (PlayerIntentGuardPolicy.shouldStartEndOfChapter(sleepTimerState.value)) {
                        sleepTimerRepository.startTimerEndOfChapter()
                    }
                }
                PlayerStateSnapshotPolicy.MODE_END_OF_TRACK -> {
                    if (PlayerIntentGuardPolicy.shouldStartEndOfTrack(sleepTimerState.value)) {
                        sleepTimerRepository.startTimerEndOfTrack()
                    }
                }
                PlayerStateSnapshotPolicy.MODE_ACTIVE -> {
                    // Remaining seconds are intentionally not persisted in the snapshot.
                    logger.d { "Skipping restore for fixed sleep timer mode due to missing remaining seconds" }
                }
                PlayerStateSnapshotPolicy.MODE_IDLE -> Unit
                else -> Unit
            }
        }
    }

    /**
     * CRITICAL: Restore saved position from database on init.
     * This ensures position is restored in all scenarios:
     * - User paused and closed app
     * - Device battery died
     * - Phone call interrupted playback
     * - Other system events
     */
    fun restorePositionFromDatabase() {
        viewModelScope.launch {
            try {
                val positionResult =
                    playbackPositionRepository
                        .getPosition(bookId)
                        .firstTerminalResult()
                        .toTypedResult()
                when (positionResult) {
                    is TypedResult.Success -> {
                        positionResult.data?.let { entity ->
                            val currentSnapshot = restoredBootstrapSnapshot.value
                            restoredBootstrapSnapshot.value =
                                RestoredBootstrapSnapshot(
                                    positionMs = entity.position.coerceAtLeast(0L),
                                    chapterIndex = entity.trackIndex.coerceAtLeast(0),
                                    playbackSpeed = currentSnapshot?.playbackSpeed ?: 1.0f,
                                    sleepTimerMode = currentSnapshot?.sleepTimerMode ?: PlayerStateSnapshotPolicy.MODE_IDLE,
                                    hasRestoredSpeed = currentSnapshot?.hasRestoredSpeed ?: false,
                                )
                            logger.d {
                                "Restored position from database: chapter=${entity.trackIndex}, position=${entity.position}ms"
                            }
                        }
                    }
                    is TypedResult.Error -> {
                        logger.w(positionResult.error.cause) {
                            "Failed to restore position: ${positionResult.error.message}"
                        }
                    }
                    is TypedResult.Loading -> Unit
                }
            } catch (e: Exception) {
                logger.e({ "Error restoring position from database" }, e)
            } finally {
                isPlaybackRestoreReady.value = true
            }
        }
    }

    /** Persists player snapshot for process-death restore. */
    fun observeSnapshotPersistence() {
        viewModelScope.launch {
            combine(uiState, sleepTimerState) { state, timerState -> state to timerState }
                .collect { (state, timerState) ->
                    if (state is PlayerState.Active) {
                        val snapshot =
                            PlayerStateSnapshotPolicy.capture(
                                bookId = bookId,
                                state = state,
                                sleepTimerState = timerState,
                            )
                        savedStateHandle[STATE_SNAPSHOT_BOOK_ID] = snapshot.bookId
                        savedStateHandle[STATE_SNAPSHOT_POSITION_MS] = snapshot.positionMs
                        savedStateHandle[STATE_SNAPSHOT_CHAPTER_INDEX] = snapshot.chapterIndex
                        savedStateHandle[STATE_SNAPSHOT_PLAYBACK_SPEED] = snapshot.playbackSpeed
                        savedStateHandle[STATE_SNAPSHOT_SLEEP_MODE] = snapshot.sleepTimerMode

                        val persistentSnapshot = PlayerStateSnapshotPolicy.normalizeForPersistence(snapshot)
                        if (PlayerStateSnapshotPolicy.shouldPersistSnapshot(lastPersistedPlayerSnapshot, persistentSnapshot)) {
                            lastPersistedPlayerSnapshot = persistentSnapshot
                            runCatching {
                                settingsRepository.updatePlayerStateSnapshot(
                                    PlayerStateSnapshotPreference(
                                        bookId = persistentSnapshot.bookId,
                                        positionMs = persistentSnapshot.positionMs,
                                        chapterIndex = persistentSnapshot.chapterIndex,
                                        playbackSpeed = persistentSnapshot.playbackSpeed,
                                        sleepTimerMode = persistentSnapshot.sleepTimerMode,
                                    ),
                                )
                            }.onFailure { error ->
                                logger.w(error) { "Failed to persist player snapshot to DataStore" }
                            }
                        }
                    }
                }
        }
    }
}
