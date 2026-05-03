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

internal data class PlaylistOrderedInsertWaitDecision(
    val maxAttempts: Int,
    val delayMs: Long,
)

internal object PlaylistOrderedInsertWaitPolicy {
    private const val MAX_ATTEMPTS = 200
    private const val DELAY_MS = 100L

    internal fun decide(): PlaylistOrderedInsertWaitDecision =
        PlaylistOrderedInsertWaitDecision(
            maxAttempts = MAX_ATTEMPTS,
            delayMs = DELAY_MS,
        )

    internal fun areAllPreviousIndicesAdded(
        index: Int,
        firstTrackIndex: Int,
        addedIndices: Set<Int>,
    ): Boolean = (0 until index).all { previousIndex -> previousIndex == firstTrackIndex || previousIndex in addedIndices }

    internal fun shouldContinueWaiting(
        waitAttempts: Int,
        maxAttempts: Int,
    ): Boolean = waitAttempts < maxAttempts
}
