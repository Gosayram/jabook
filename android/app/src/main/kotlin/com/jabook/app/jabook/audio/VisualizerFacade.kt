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

import kotlinx.coroutines.flow.StateFlow

/**
 * Facade for audio visualizer operations, reducing delegation boilerplate in AudioPlayerService.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates all visualizer queries and commands in one place.
 */
internal class VisualizerFacade(
    private val getAudioVisualizerManager: () -> AudioVisualizerManager?,
    private val getExoPlayerAudioSessionId: () -> Int,
) {
    fun getAudioSessionId(): Int = getExoPlayerAudioSessionId()

    fun getWaveformData(): StateFlow<FloatArray>? = getAudioVisualizerManager()?.waveformData

    fun initialize() {
        val sessionId = getExoPlayerAudioSessionId()
        if (sessionId != 0) {
            getAudioVisualizerManager()?.initialize(sessionId)
        }
    }

    fun setEnabled(enabled: Boolean) {
        getAudioVisualizerManager()?.setEnabled(enabled)
    }
}
