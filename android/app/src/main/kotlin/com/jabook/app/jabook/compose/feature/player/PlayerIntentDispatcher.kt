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

import android.content.Context
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Intent interpretation pipeline: reduction, chapter-navigation resolution,
 * side-effect handling, and command dispatch.
 *
 * @param uiState Current player UI state
 * @param commandExecutor Executor translating commands into player actions
 * @param commandChannel Channel for dispatched player commands
 * @param context Application context for localized strings
 * @param visualizerMode Current visualizer mode
 * @param settingsRepository DataStore-backed settings repository
 * @param chapterRepeatHandler Chapter repeat handler
 * @param abRepeatHandler A-B repeat handler
 * @param viewModelScope Coroutine scope for async operations
 * @param loggerFactory Logger factory
 * @param emitEffect Callback for emitting one-shot UI effects
 */
internal class PlayerIntentDispatcher(
    private val uiState: StateFlow<PlayerState>,
    private val commandExecutor: PlayerCommandExecutor,
    private val commandChannel: Channel<PlayerCommand>,
    private val context: Context,
    private val visualizerMode: StateFlow<Int>,
    private val settingsRepository: ProtoSettingsRepository,
    private val chapterRepeatHandler: PlayerChapterRepeatHandler,
    private val abRepeatHandler: PlayerABRepeatHandler,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
    private val emitEffect: (PlayerEffect) -> Unit,
) {
    private val logger: Logger = loggerFactory.get("PlayerIntentDispatcher")

    fun dispatch(intent: PlayerIntent) {
        logger.d { "PlayerIntent received: $intent" }
        val currentState = uiState.value
        val chapterNavigationDecision = resolveChapterNavigationIntent(intent, currentState)
        val effectiveIntent = chapterNavigationDecision.intent
        val reducedState = PlayerReducer.reduce(currentState, effectiveIntent)
        if (
            currentState is PlayerState.Loading &&
            reducedState is PlayerState.Loading &&
            effectiveIntent.isPlaybackControlIntent()
        ) {
            emitEffect(PlayerEffect.ShowSnackbar("Player is not ready yet"))
            return
        }
        handleIntentSideEffects(
            intent = effectiveIntent,
            currentState = currentState,
            reducedState = reducedState,
        )
        maybeEmitChapterNavigationUndo(chapterNavigationDecision)
    }

    private fun resolveChapterNavigationIntent(
        intent: PlayerIntent,
        state: PlayerState,
    ): ChapterNavigationDecision =
        (state as? PlayerState.Active)?.let { activeState ->
            ChapterNavigationIntentPolicy.resolve(intent = intent, state = activeState)
        } ?: ChapterNavigationDecision(intent = intent)

    private fun maybeEmitChapterNavigationUndo(decision: ChapterNavigationDecision) {
        val targetChapter = decision.movedToChapterDisplayIndex ?: return
        val undoChapterIndex = decision.undoChapterIndex ?: return
        emitEffect(
            PlayerEffect.ShowSnackbar(
                message = context.getString(R.string.playerChapterNavigationSnackbar, targetChapter),
                actionLabel = context.getString(R.string.undoAction),
                actionIntent = PlayerIntent.SelectChapter(chapterIndex = undoChapterIndex),
            ),
        )
    }

    private fun handleIntentSideEffects(
        intent: PlayerIntent,
        currentState: PlayerState,
        reducedState: PlayerState,
    ) {
        if (handleCommandIntent(intent, currentState, reducedState)) return
        when (intent) {
            PlayerIntent.ToggleChapterRepeat -> chapterRepeatHandler.onToggleChapterRepeat(reducedState)
            is PlayerIntent.CycleVisualizerMode -> {
                val currentMode = visualizerMode.value
                val nextMode = (currentMode + 1) % 4
                viewModelScope.launch {
                    settingsRepository.updateAudioVisualizerMode(nextMode)
                }
            }
            PlayerIntent.ToggleABRepeat -> abRepeatHandler.onToggleABRepeat()
            is PlayerIntent.SetEqualizerPreset -> {
                viewModelScope.launch {
                    runCatching { settingsRepository.updateEqualizerPreset(intent.presetName) }
                        .onFailure { error ->
                            logger.w(error) { "Failed to update EQ preset" }
                        }
                }
            }
            is PlayerIntent.ReportError -> {
                val reason = (reducedState as? PlayerState.Error)?.message ?: intent.reason
                emitEffect(PlayerEffect.ShowError(reason))
            }
            else -> Unit
        }
    }

    private fun handleCommandIntent(
        intent: PlayerIntent,
        currentState: PlayerState,
        reducedState: PlayerState,
    ): Boolean =
        if (!PlayerIntentCommandRouter.isCommandIntent(intent)) {
            false
        } else {
            val command = PlayerIntentCommandRouter.routeIntent(intent, currentState, reducedState)
            if (command == null) {
                logger.d { "Command intent produced no command: $intent" }
            } else {
                dispatchCommand(command)
            }
            true
        }

    private fun dispatchCommand(command: PlayerCommand) {
        viewModelScope.launch {
            runCatching { commandChannel.send(command) }
                .onFailure { error ->
                    logger.w(error) { "Command dispatch failed for $command" }
                }
        }
    }
}
