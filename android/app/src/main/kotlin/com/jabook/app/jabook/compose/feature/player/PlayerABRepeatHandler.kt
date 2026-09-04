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

import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A-B repeat state machine: point selection, validation, looping seeks, and reset on chapter change.
 *
 * @param playerController Controller exposing position/chapter flows and seeking
 * @param uiState Current player UI state
 * @param viewModelScope Coroutine scope for collectors
 * @param emitEffect Callback for emitting one-shot UI effects
 */
internal class PlayerABRepeatHandler(
    private val playerController: AudioPlayerController,
    private val uiState: StateFlow<PlayerState>,
    private val viewModelScope: CoroutineScope,
    private val emitEffect: (PlayerEffect) -> Unit,
) {
    private val _abRepeatState = MutableStateFlow(ABRepeatState())

    /** Current A-B repeat state. */
    val abRepeatState: StateFlow<ABRepeatState> = _abRepeatState.asStateFlow()

    /** Handles [PlayerIntent.ToggleABRepeat] against the current active state. */
    fun onToggleABRepeat() {
        val currentABState = _abRepeatState.value
        val activeState = uiState.value as? PlayerState.Active ?: return
        val chapterIndex = activeState.currentChapterIndex
        val position = playerController.currentPosition.value
        when (currentABState.phase) {
            ABRepeatPhase.INACTIVE -> {
                _abRepeatState.value =
                    ABRepeatState(pointA = position, chapterIndex = chapterIndex, phase = ABRepeatPhase.A_SET)
            }
            ABRepeatPhase.A_SET -> {
                when {
                    currentABState.chapterIndex != chapterIndex -> {
                        _abRepeatState.value =
                            ABRepeatState(pointA = position, chapterIndex = chapterIndex, phase = ABRepeatPhase.A_SET)
                    }
                    !isValidABRepeatRange(currentABState.pointA, position) -> {
                        emitEffect(PlayerEffect.ShowSnackbar("Point B must be after point A"))
                    }
                    else -> {
                        _abRepeatState.value =
                            ABRepeatState(
                                pointA = currentABState.pointA,
                                pointB = position,
                                chapterIndex = chapterIndex,
                                phase = ABRepeatPhase.ACTIVE,
                            )
                    }
                }
            }
            ABRepeatPhase.ACTIVE -> {
                _abRepeatState.value = ABRepeatState()
            }
        }
    }

    /** Observes playback and performs loop seeks while A-B repeat is active. */
    fun observePosition() {
        viewModelScope.launch {
            combine(
                playerController.currentPosition,
                playerController.currentChapterIndex,
                _abRepeatState,
            ) { position, chapterIndex, abState ->
                Triple(position, chapterIndex, abState)
            }.collect { (position, chapterIndex, abState) ->
                if (abState.phase != ABRepeatPhase.INACTIVE && abState.chapterIndex != chapterIndex) {
                    _abRepeatState.value = ABRepeatState()
                } else if (
                    abState.phase == ABRepeatPhase.ACTIVE &&
                    isValidABRepeatRange(abState.pointA, abState.pointB) &&
                    position >= abState.pointB
                ) {
                    playerController.seekTo(abState.pointA)
                }
            }
        }
    }

    /** Clears A-B repeat state (called when the chapter changes). */
    fun reset() {
        _abRepeatState.value = ABRepeatState()
    }
}
