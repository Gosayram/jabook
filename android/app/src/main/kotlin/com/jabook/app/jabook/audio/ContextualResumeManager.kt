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

import androidx.compose.runtime.Immutable

internal fun interface SpeechSegmentAnalyzer {
    fun findLastSentenceStart(
        bookId: String,
        positionMs: Long,
        lookbackMs: Long,
    ): Long
}

internal class ContextualResumeManager(
    private val speechAnalyzer: SpeechSegmentAnalyzer,
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() },
) {
    @Immutable
    data class ResumeContext(
        val pauseDurationMs: Long,
        val rewindMs: Long,
        val shouldShowRecap: Boolean,
        val recapStartMs: Long,
    )

    fun buildResumeContext(
        bookId: String,
        currentPositionMs: Long,
        lastPausedAtMs: Long,
    ): ResumeContext {
        if (lastPausedAtMs <= 0L) {
            return ResumeContext(
                pauseDurationMs = 0L,
                rewindMs = 0L,
                shouldShowRecap = false,
                recapStartMs = 0L,
            )
        }
        val nowMs = nowMsProvider()
        // If the system clock adjusted backward (nowMs < lastPausedAtMs), pause
        // duration coerces to 0L, triggering the short-rewind branch. This is
        // intentional — a clock-skewed pause of unknown length is treated as brief.
        val pauseDurationMs = (nowMs - lastPausedAtMs).coerceAtLeast(0L)

        return when {
            pauseDurationMs < ONE_HOUR_MS -> {
                ResumeContext(
                    pauseDurationMs = pauseDurationMs,
                    rewindMs =
                        ResumeRewindPolicy.resolveRewindMs(
                            pauseDurationMs = pauseDurationMs,
                            configuredSeconds = DEFAULT_SHORT_REWIND_SECONDS,
                            mode = ResumeRewindMode.SMART,
                            aggressiveness = 1.0f,
                        ),
                    shouldShowRecap = false,
                    recapStartMs = 0L,
                )
            }

            pauseDurationMs < ONE_DAY_MS -> {
                val sentenceBoundary =
                    speechAnalyzer
                        .findLastSentenceStart(
                            bookId = bookId,
                            positionMs = currentPositionMs,
                            lookbackMs = SENTENCE_LOOKBACK_MS,
                        ).coerceIn(0L, currentPositionMs)
                ResumeContext(
                    pauseDurationMs = pauseDurationMs,
                    rewindMs = currentPositionMs - sentenceBoundary,
                    shouldShowRecap = false,
                    recapStartMs = 0L,
                )
            }

            else -> {
                ResumeContext(
                    pauseDurationMs = pauseDurationMs,
                    rewindMs = 0L,
                    shouldShowRecap = true,
                    recapStartMs = (currentPositionMs - RECAP_WINDOW_MS).coerceAtLeast(0L),
                )
            }
        }
    }

    private companion object {
        private const val ONE_HOUR_MS: Long = 60L * 60_000L
        private const val ONE_DAY_MS: Long = 24L * 60L * 60_000L
        private const val SENTENCE_LOOKBACK_MS: Long = 30_000L
        private const val RECAP_WINDOW_MS: Long = 2L * 60_000L
        private const val DEFAULT_SHORT_REWIND_SECONDS: Int = 10
    }
}
