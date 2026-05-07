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

package com.jabook.app.jabook.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.util.LogUtils

/**
 * Facade for player configuration and active player resolution.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates player setup, configuration, and active player resolution.
 *
 * @param getPlayerConfigurator provider for PlayerConfigurator
 * @param getExoPlayer provider for singleton ExoPlayer
 * @param getCrossFadePlayer provider for CrossFadePlayer (nullable)
 * @param getCrossfadeHandler provider for CrossfadeHandler (nullable)
 */
internal class PlayerFacade(
    private val getPlayerConfigurator: () -> PlayerConfigurator?,
    private val getExoPlayer: () -> ExoPlayer,
    private val getCrossFadePlayer: () -> CrossFadePlayer?,
    private val getCrossfadeHandler: () -> CrossfadeHandler?,
) {
    /** Gets the player listener from PlayerConfigurator. */
    val playerListener: PlayerListener?
        get() = getPlayerConfigurator()?.playerListener

    /** Configures ExoPlayer with listener and additional settings. */
    fun configurePlayer() {
        getPlayerConfigurator()?.configurePlayer() ?: run {
            LogUtils.e(TAG, "PlayerConfigurator not initialized")
        }
    }

    /** Configures ExoPlayer with audio processors based on settings. */
    @OptIn(UnstableApi::class)
    fun configureExoPlayer(settings: AudioProcessingSettings) {
        getPlayerConfigurator()?.configureExoPlayer(settings) ?: run {
            LogUtils.e(TAG, "PlayerConfigurator not initialized")
        }
    }

    /** Returns the active ExoPlayer (custom with processors or singleton). */
    fun getActivePlayer(): ExoPlayer {
        val settings = getPlayerConfigurator()?.audioProcessingSettings
        if (settings?.isCrossfadeEnabled == true) {
            getCrossFadePlayer()?.let {
                return it.getActivePlayer()
            }
        }
        return getPlayerConfigurator()?.getActivePlayer(getExoPlayer()) ?: getExoPlayer()
    }

    /** Triggers crossfade transition via [CrossfadeHandler]. */
    fun triggerCrossfadeTransition() {
        getCrossfadeHandler()?.triggerCrossfadeTransition()
    }

    private companion object {
        private const val TAG = "PlayerFacade"
    }
}
