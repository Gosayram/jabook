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
        silenceThresholdDb: Float? = null,
        minSilenceMs: Long = DEFAULT_MIN_CHAPTER_SILENCE_MS,
    ): List<CandidateBoundary> {
        if (rmsDbValues.isEmpty() || windowStepMs <= 0L || minSilenceMs <= 0L) return emptyList()
        val effectiveThresholdDb = silenceThresholdDb ?: resolveAdaptiveSilenceThresholdDb(rmsDbValues)

        val requiredSilentWindows = (minSilenceMs / windowStepMs).coerceAtLeast(1L).toInt()
        val result = mutableListOf<CandidateBoundary>()

        var inSilence = false
        var silenceStartIndex = 0

        rmsDbValues.forEachIndexed { index, rmsDb ->
            val isSilent = rmsDb <= effectiveThresholdDb
            when {
                isSilent && !inSilence -> {
                    inSilence = true
                    silenceStartIndex = index
                }
                !isSilent && inSilence -> {
                    val silentWindows = index - silenceStartIndex
                    if (silentWindows >= requiredSilentWindows) {
                        val boundaryMs = index * windowStepMs
                        val minSilenceDb =
                            rmsDbValues
                                .subList(silenceStartIndex, index.coerceAtMost(rmsDbValues.size))
                                .minOrNull()
                                ?: effectiveThresholdDb
                        val confidence =
                            confidenceForWindows(
                                silentWindows = silentWindows,
                                requiredSilentWindows = requiredSilentWindows,
                                minSilenceDb = minSilenceDb,
                                thresholdDb = effectiveThresholdDb,
                            )
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
        minSilenceDb: Float,
        thresholdDb: Float,
    ): Float {
        if (requiredSilentWindows <= 0) return 0f
        val durationRatio = (silentWindows.toFloat() / requiredSilentWindows.toFloat()).coerceIn(0f, 1f)
        val depthDb = (thresholdDb - minSilenceDb).coerceAtLeast(0f)
        val depthRatio = (depthDb / TARGET_SILENCE_DEPTH_DB).coerceIn(0f, 1f)
        return (durationRatio * DURATION_WEIGHT + depthRatio * DEPTH_WEIGHT).coerceIn(0f, MAX_CONFIDENCE)
    }

    internal fun resolveAdaptiveSilenceThresholdDb(rmsDbValues: List<Float>): Float {
        if (rmsDbValues.isEmpty()) return DEFAULT_SILENCE_THRESHOLD_DB
        val sorted = rmsDbValues.sorted()
        val percentileIndex = (sorted.lastIndex * NOISE_FLOOR_PERCENTILE).toInt().coerceIn(0, sorted.lastIndex)
        val noiseFloorDb = sorted[percentileIndex]
        // Threshold should be above noise floor, but still conservative enough for speech pauses.
        val adaptive = noiseFloorDb + ADAPTIVE_THRESHOLD_MARGIN_DB
        return adaptive.coerceIn(MIN_ADAPTIVE_THRESHOLD_DB, MAX_ADAPTIVE_THRESHOLD_DB)
    }

    internal const val DEFAULT_MIN_CHAPTER_SILENCE_MS: Long = 2_000L
    internal const val DEFAULT_SILENCE_THRESHOLD_DB: Float = -40f
    internal const val DEFAULT_WINDOW_STEP_MS: Long = 100L
    internal const val MIN_ADAPTIVE_THRESHOLD_DB: Float = -55f
    internal const val MAX_ADAPTIVE_THRESHOLD_DB: Float = -30f
    internal const val ADAPTIVE_THRESHOLD_MARGIN_DB: Float = 6f
    internal const val TARGET_SILENCE_DEPTH_DB: Float = 12f
    private const val NOISE_FLOOR_PERCENTILE: Float = 0.2f
    private const val DURATION_WEIGHT: Float = 0.7f
    private const val DEPTH_WEIGHT: Float = 0.3f
    private const val MAX_CONFIDENCE: Float = 1f
}
