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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEEKBAR_WAVEFORM_CACHE_SIZE: Int = 1000

/**
 * Maintains the rolling seekbar waveform window from visualizer chunks.
 *
 * @param visualizerWaveformData Incoming visualizer waveform chunks
 * @param viewModelScope Coroutine scope for collectors
 */
internal class PlayerSeekbarWaveformHandler(
    private val visualizerWaveformData: StateFlow<FloatArray>,
    private val viewModelScope: CoroutineScope,
) {
    private val _seekbarWaveformData = MutableStateFlow(FloatArray(SEEKBAR_WAVEFORM_CACHE_SIZE))

    /** Latest merged waveform window for the seekbar. */
    val seekbarWaveformData: StateFlow<FloatArray> = _seekbarWaveformData.asStateFlow()

    fun observe() {
        viewModelScope.launch {
            visualizerWaveformData.collect { chunk ->
                _seekbarWaveformData.value =
                    withContext(Dispatchers.Default) {
                        mergeWaveformWindow(
                            currentWindow = _seekbarWaveformData.value,
                            incomingChunk = chunk,
                            targetSize = SEEKBAR_WAVEFORM_CACHE_SIZE,
                        )
                    }
            }
        }
    }
}

private fun mergeWaveformWindow(
    currentWindow: FloatArray,
    incomingChunk: FloatArray,
    targetSize: Int,
): FloatArray {
    if (targetSize <= 0) return FloatArray(0)
    if (incomingChunk.isEmpty()) return currentWindow

    if (incomingChunk.size >= targetSize) {
        val result = FloatArray(targetSize)
        val start = incomingChunk.size - targetSize
        for (i in 0 until targetSize) {
            result[i] = kotlin.math.abs(incomingChunk[start + i]).coerceIn(0f, 1f)
        }
        return result
    }

    val shift = incomingChunk.size
    val keep = (targetSize - shift).coerceAtLeast(0)
    val result = FloatArray(targetSize)

    if (keep > 0 && currentWindow.isNotEmpty()) {
        val copyLength = minOf(keep, currentWindow.size)
        val fromIndex = (currentWindow.size - copyLength).coerceAtLeast(0)
        System.arraycopy(currentWindow, fromIndex, result, keep - copyLength, copyLength)
    }

    for (i in incomingChunk.indices) {
        result[keep + i] = kotlin.math.abs(incomingChunk[i]).coerceIn(0f, 1f)
    }

    return result
}
