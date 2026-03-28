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

import androidx.media3.common.C

internal object EndOfFileDetectionPolicy {
    private const val MIN_END_OF_FILE_THRESHOLD_MS = 2000L
    private const val MAX_END_OF_FILE_THRESHOLD_MS = 5000L
    private const val END_OF_FILE_THRESHOLD_PERCENT = 0.01

    fun calculateThresholdMs(durationMs: Long): Long {
        if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
            return MIN_END_OF_FILE_THRESHOLD_MS
        }

        val proportionalThreshold = (durationMs * END_OF_FILE_THRESHOLD_PERCENT).toLong()
        return proportionalThreshold.coerceIn(
            minimumValue = MIN_END_OF_FILE_THRESHOLD_MS,
            maximumValue = MAX_END_OF_FILE_THRESHOLD_MS,
        )
    }
}
