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
 * Post-processing for detected chapter boundaries.
 *
 * Removes low-confidence boundaries and collapses boundaries that are too close.
 */
internal object ChapterDetectionResultPolicy {
    internal fun normalizeCandidates(
        candidates: List<ChapterDetectionPolicy.CandidateBoundary>,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        minGapMs: Long = DEFAULT_MIN_GAP_MS,
    ): List<ChapterDetectionPolicy.CandidateBoundary> {
        if (candidates.isEmpty()) return emptyList()

        val filtered =
            candidates
                .asSequence()
                .filter { it.confidence >= minConfidence && it.startMs >= 0L }
                .sortedBy { it.startMs }
                .toList()

        if (filtered.isEmpty()) return emptyList()

        val normalized = mutableListOf<ChapterDetectionPolicy.CandidateBoundary>()
        filtered.forEach { candidate ->
            val prev = normalized.lastOrNull()
            if (prev == null) {
                normalized += candidate
                return@forEach
            }
            if (candidate.startMs - prev.startMs < minGapMs) {
                if (candidate.confidence > prev.confidence) {
                    normalized[normalized.lastIndex] = candidate
                }
            } else {
                normalized += candidate
            }
        }
        return normalized
    }

    internal const val DEFAULT_MIN_GAP_MS: Long = 60_000L
    internal const val DEFAULT_MIN_CONFIDENCE: Float = 0.75f
}
