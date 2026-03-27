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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local bridge for audio visualizer data between AudioPlayerService and Compose UI.
 *
 * UI reads this flow via ViewModel, while service updates it from AudioVisualizerManager.
 */
@Singleton
public class AudioVisualizerStateBridge
    @Inject
    constructor() {
        private val _waveformData = MutableStateFlow(FloatArray(DEFAULT_CAPTURE_SIZE))
        public val waveformData: StateFlow<FloatArray> = _waveformData.asStateFlow()

        public fun updateWaveform(data: FloatArray) {
            _waveformData.value = data
        }

        public fun reset() {
            _waveformData.value = FloatArray(DEFAULT_CAPTURE_SIZE)
        }

        private companion object {
            private const val DEFAULT_CAPTURE_SIZE = 256
        }
    }
