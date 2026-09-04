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
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.SleepTimerPersistence
import com.jabook.app.jabook.audio.processors.EqContextRecommendationPolicy
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One-shot session hint observers: sleep-timer resume hint, phone-call bookmark notice,
 * smart-resume recap suggestion, and context EQ recommendation.
 *
 * @param context Application context for localized strings and output detection
 * @param uiState Current player UI state
 * @param playerController Controller exposing smart-resume suggestions
 * @param viewModelScope Coroutine scope for collectors
 * @param emitEffect Callback for emitting one-shot UI effects
 */
internal class PlayerSessionHintsHandler(
    private val context: Context,
    private val uiState: StateFlow<PlayerState>,
    private val playerController: AudioPlayerController,
    private val viewModelScope: CoroutineScope,
    private val emitEffect: (PlayerEffect) -> Unit,
) {
    private var hasShownSleepTimerResumeHint: Boolean = false
    private var hasShownEqRecommendation: Boolean = false

    fun observeSleepTimerResumeHint() {
        viewModelScope.launch {
            uiState.collect { state ->
                val activeState = state as? PlayerState.Active ?: return@collect
                val wasLastStopBySleepTimer = wasLastStoppedBySleepTimerFlagSet()
                if (
                    SleepTimerResumeHintPolicy.shouldShowHint(
                        wasLastStopBySleepTimer = wasLastStopBySleepTimer,
                        isPlaying = activeState.isPlaying,
                        hasAlreadyShownInSession = hasShownSleepTimerResumeHint,
                    )
                ) {
                    hasShownSleepTimerResumeHint = true
                    emitEffect(PlayerEffect.ShowSnackbar(context.getString(R.string.sleepTimerResumeHint)))
                }
            }
        }
    }

    fun observePhoneCallBookmarkHint() {
        viewModelScope.launch {
            uiState.collect { state ->
                val activeState = state as? PlayerState.Active ?: return@collect
                if (!activeState.isPlaying) return@collect
                if (AudioPlayerService.phoneCallBookmarkCreated) {
                    AudioPlayerService.phoneCallBookmarkCreated = false
                    emitEffect(PlayerEffect.ShowSnackbar(context.getString(R.string.phoneCallBookmarkSnackbar)))
                }
            }
        }
    }

    fun observeEqRecommendation() {
        viewModelScope.launch {
            uiState.collect { state ->
                val activeState = state as? PlayerState.Active ?: return@collect
                if (hasShownEqRecommendation) return@collect
                hasShownEqRecommendation = true

                val hourOfDay =
                    java.time.LocalTime
                        .now()
                        .hour
                val audioOutputType = EqContextRecommendationPolicy.detectAudioOutputType(context)
                val recommendation = EqContextRecommendationPolicy(context).recommend(hourOfDay, audioOutputType, null)
                if (recommendation != null) {
                    emitEffect(
                        PlayerEffect.ShowSnackbar(
                            message = context.getString(R.string.eq_recommendation_message, recommendation.displayName),
                            actionLabel = context.getString(R.string.eq_recommendation_apply),
                            actionIntent = PlayerIntent.SetEqualizerPreset(recommendation.name),
                        ),
                    )
                }
            }
        }
    }

    private fun wasLastStoppedBySleepTimerFlagSet(): Boolean {
        val prefs = context.getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(SleepTimerPersistence.KEY_LAST_STOPPED_BY_SLEEP_TIMER, false)
    }
}
