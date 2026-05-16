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
 * Maps steering-wheel next/previous buttons to audiobook-friendly actions.
 */
internal object SteeringWheelActionPolicy {
    private const val DEFAULT_RESTART_THRESHOLD_MS: Long = 3_000L

    enum class NextAction {
        NEXT_CHAPTER,
        FORWARD_SECONDS,
    }

    enum class PreviousAction {
        PREVIOUS_CHAPTER,
        RESTART_CHAPTER,
    }

    fun resolveNextAction(
        currentChapterIndex: Int,
        totalChapters: Int,
    ): NextAction =
        if (totalChapters > 0 && currentChapterIndex < totalChapters - 1) {
            NextAction.NEXT_CHAPTER
        } else {
            NextAction.FORWARD_SECONDS
        }

    fun resolvePreviousAction(
        currentPositionMs: Long,
        restartThresholdMs: Long = DEFAULT_RESTART_THRESHOLD_MS,
    ): PreviousAction =
        if (currentPositionMs > restartThresholdMs) {
            PreviousAction.RESTART_CHAPTER
        } else {
            PreviousAction.PREVIOUS_CHAPTER
        }
}
