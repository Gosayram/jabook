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

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.util.runCatchingCancelable
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * P-92: Book and audio settings operations extracted from PlayerViewModel.
 *
 * @param bookId Current book identifier
 * @param updateBookSettingsUseCase Use case for updating book settings
 * @param settingsRepository Repository for audio settings
 * @param viewModelScope Coroutine scope for async operations
 * @param loggerFactory Logger factory
 * @param reportError Callback to report errors to the UI
 */
internal class PlayerSettingsHandler(
    private val bookId: String,
    private val updateBookSettingsUseCase: UpdateBookSettingsUseCase,
    private val settingsRepository: ProtoSettingsRepository,
    private val viewModelScope: CoroutineScope,
    loggerFactory: LoggerFactory,
    private val reportError: (String) -> Unit,
) {
    private val logger: Logger = loggerFactory.get("PlayerSettingsHandler")

    fun updateBookSeekSettings(
        rewindSeconds: Int?,
        forwardSeconds: Int?,
    ) {
        viewModelScope.launch {
            runCatchingCancelable { updateBookSettingsUseCase(bookId, rewindSeconds, forwardSeconds) }
                .onFailure { error ->
                    logger.e({ "Failed to update book seek settings" }, error)
                    reportError("Failed to update seek settings")
                }
        }
    }

    fun resetBookSeekSettings() {
        viewModelScope.launch {
            runCatchingCancelable { updateBookSettingsUseCase.resetForBook(bookId) }
                .onFailure { error ->
                    logger.e({ "Failed to reset book seek settings" }, error)
                    reportError("Failed to reset seek settings")
                }
        }
    }

    fun updateAudioSettings(
        volumeBoostLevel: com.jabook.app.jabook.audio.processors.VolumeBoostLevel? = null,
        skipSilence: Boolean? = null,
        skipSilenceThresholdDb: Float? = null,
        skipSilenceMinMs: Int? = null,
        skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode? = null,
        normalizeVolume: Boolean? = null,
        speechEnhancer: Boolean? = null,
        autoVolumeLeveling: Boolean? = null,
    ) {
        viewModelScope.launch {
            runCatchingCancelable {
                settingsRepository.updateAudioSettings(
                    volumeBoost = volumeBoostLevel?.name,
                    skipSilence = skipSilence,
                    skipSilenceThresholdDb = skipSilenceThresholdDb,
                    skipSilenceMinMs = skipSilenceMinMs,
                    skipSilenceMode = skipSilenceMode,
                    normalizeVolume = normalizeVolume,
                    speechEnhancer = speechEnhancer,
                    autoVolumeLeveling = autoVolumeLeveling,
                )
            }.onFailure { error ->
                logger.e({ "Failed to update audio settings" }, error)
                reportError("Failed to update audio settings")
            }
        }
    }
}
