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

internal object BookCompletionIndexPolicy {
    fun resolveCompletionIndex(
        currentIndex: Int,
        totalTracks: Int,
        savedCompletedIndex: Int,
        currentPositionMs: Long,
        durationMs: Long,
    ): Int {
        if (totalTracks <= 0) {
            return currentIndex
        }

        val isInvalidIndex = currentIndex == 0 || currentIndex < 0 || currentIndex >= totalTracks
        if (!isInvalidIndex) {
            return currentIndex
        }

        if (savedCompletedIndex >= 0 && savedCompletedIndex < totalTracks) {
            return savedCompletedIndex
        }

        if (currentPositionMs > 0 && durationMs > 0) {
            return totalTracks - 1
        }

        return if (currentIndex >= totalTracks) {
            totalTracks - 1
        } else {
            currentIndex
        }
    }
}
