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

import com.jabook.app.jabook.audio.WaveformCache
import com.jabook.app.jabook.audio.WaveformExtractor
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Number of bars rendered by [com.jabook.app.jabook.compose.feature.player.SquigglySlider]. */
internal const val SEEKBAR_WAVEFORM_BARS: Int = 72

/**
 * Feeds the seekbar waveform from whole-file per-second peaks of the current chapter.
 *
 * On chapter change: loads from cache, or extracts via [WaveformExtractor] with
 * progressive emission so the bar fills left-to-right during the single decode pass.
 * Extraction is cancelled by [collectLatest] when the chapter changes; a generation
 * counter guards late partial emissions from overwriting a newer track's data.
 *
 * @param bookId Current book identifier
 * @param getChaptersUseCase Use case for retrieving book chapters
 * @param playerController Controller exposing the current chapter index
 * @param waveformExtractor Whole-file peak extractor
 * @param waveformCache Disk + memory cache for extracted peaks
 * @param viewModelScope Coroutine scope for collectors
 */
internal class PlayerSeekbarWaveformHandler(
    private val bookId: String,
    private val getChaptersUseCase: GetChaptersUseCase,
    private val playerController: AudioPlayerController,
    private val waveformExtractor: WaveformExtractor,
    private val waveformCache: WaveformCache,
    private val viewModelScope: CoroutineScope,
) {
    private val generation = AtomicLong(0)

    private val _seekbarWaveformData = MutableStateFlow(FloatArray(0))

    /** Downsampled whole-file waveform (0..1 per bar) for the seekbar. */
    val seekbarWaveformData: StateFlow<FloatArray> = _seekbarWaveformData.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe() {
        viewModelScope.launch {
            combine(
                getChaptersUseCase(bookId).map(::sortChaptersForPlayback),
                playerController.currentChapterIndex,
            ) { chapters, index ->
                chapters.getOrNull(index)?.let { chapter ->
                    val path = chapter.fileUrl
                    if (path.isNullOrBlank()) null else path to chapter.duration.inWholeMilliseconds
                }
            }.distinctUntilChanged()
                .collectLatest { track ->
                    val currentGeneration = generation.incrementAndGet()
                    if (track == null) {
                        _seekbarWaveformData.value = FloatArray(0)
                        return@collectLatest
                    }
                    val (path, durationMs) = track

                    val key = waveformCache.cacheKey(path)
                    val cached = key?.let { waveformCache.load(it) }
                    if (cached != null && cached.isNotEmpty()) {
                        _seekbarWaveformData.value = downsampleToBars(cached, SEEKBAR_WAVEFORM_BARS)
                        return@collectLatest
                    }

                    _seekbarWaveformData.value = FloatArray(0)
                    val peaks =
                        waveformExtractor.extract(path, durationMs) { partial ->
                            if (generation.get() == currentGeneration) {
                                _seekbarWaveformData.value = downsampleToBars(partial, SEEKBAR_WAVEFORM_BARS)
                            }
                        }
                    if (peaks.isEmpty() || generation.get() != currentGeneration) return@collectLatest
                    if (key != null) waveformCache.save(key, peaks)
                    _seekbarWaveformData.value = downsampleToBars(peaks, SEEKBAR_WAVEFORM_BARS)
                }
        }
    }
}

/**
 * Averages per-second [peaks] into [bars] buckets (0..1). Empty input yields an
 * empty array so the slider keeps its squiggle fallback.
 */
internal fun downsampleToBars(
    peaks: FloatArray,
    bars: Int,
): FloatArray {
    if (peaks.isEmpty() || bars <= 0) return FloatArray(0)
    val outputBars = minOf(bars, peaks.size)
    val result = FloatArray(outputBars)
    val bucketSize = peaks.size.toFloat() / outputBars
    for (bar in 0 until outputBars) {
        val start = (bar * bucketSize).toInt()
        val end = (((bar + 1) * bucketSize).toInt().coerceAtMost(peaks.size)).coerceAtLeast(start + 1)
        var sum = 0f
        for (index in start until end) {
            sum += peaks[index]
        }
        result[bar] = (sum / (end - start)).coerceIn(0f, 1f)
    }
    return result
}
