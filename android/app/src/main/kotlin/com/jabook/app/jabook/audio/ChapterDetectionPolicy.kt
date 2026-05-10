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

/**
 * Detects probable chapter boundaries from per-window RMS levels in dB.
 *
 * A boundary is emitted when a silence segment with duration >= [minSilenceMs]
 * ends and speech resumes.
 */
internal object ChapterDetectionPolicy {
    internal data class CandidateBoundary(
        val startMs: Long,
        val confidence: Float,
    )

    internal fun detectCandidates(
        rmsDbValues: List<Float>,
        windowStepMs: Long = DEFAULT_WINDOW_STEP_MS,
        silenceThresholdDb: Float = DEFAULT_SILENCE_THRESHOLD_DB,
        minSilenceMs: Long = DEFAULT_MIN_CHAPTER_SILENCE_MS,
    ): List<CandidateBoundary> {
        if (rmsDbValues.isEmpty() || windowStepMs <= 0L || minSilenceMs <= 0L) return emptyList()

        val requiredSilentWindows = (minSilenceMs / windowStepMs).coerceAtLeast(1L).toInt()
        val result = mutableListOf<CandidateBoundary>()

        var inSilence = false
        var silenceStartIndex = 0

        rmsDbValues.forEachIndexed { index, rmsDb ->
            val isSilent = rmsDb <= silenceThresholdDb
            when {
                isSilent && !inSilence -> {
                    inSilence = true
                    silenceStartIndex = index
                }
                !isSilent && inSilence -> {
                    val silentWindows = index - silenceStartIndex
                    if (silentWindows >= requiredSilentWindows) {
                        val boundaryMs = index * windowStepMs
                        val confidence = confidenceForWindows(silentWindows, requiredSilentWindows)
                        result += CandidateBoundary(startMs = boundaryMs, confidence = confidence)
                    }
                    inSilence = false
                }
            }
        }

        return result
    }

    private fun confidenceForWindows(
        silentWindows: Int,
        requiredSilentWindows: Int,
    ): Float {
        if (requiredSilentWindows <= 0) return 0f
        val ratio = silentWindows.toFloat() / requiredSilentWindows.toFloat()
        return ratio.coerceIn(0f, MAX_CONFIDENCE)
    }

    internal const val DEFAULT_MIN_CHAPTER_SILENCE_MS: Long = 2_000L
    internal const val DEFAULT_SILENCE_THRESHOLD_DB: Float = -40f
    internal const val DEFAULT_WINDOW_STEP_MS: Long = 100L
    private const val MAX_CONFIDENCE: Float = 1f
}
