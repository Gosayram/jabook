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

package com.jabook.app.jabook.audio.processors

import kotlin.math.pow

internal object SkipSilenceThresholdPolicy {
    private const val MIN_THRESHOLD_DB = -40f
    private const val MAX_THRESHOLD_DB = -20f
    private const val DEFAULT_THRESHOLD_DB = -32f
    private const val MIN_SILENCE_MS = 150
    private const val MAX_SILENCE_MS = 300
    private const val DEFAULT_MIN_SILENCE_MS = 250

    /** Minimum retain window in ms (below this, speech-to-silence transitions clip). */
    private const val MIN_RETAIN_WINDOW_MS = 50

    /** Maximum retain window in ms (above this, skip-silence effectiveness drops). */
    private const val MAX_RETAIN_WINDOW_MS = 80

    /** Default retain window: 65 ms — a good balance between smoothness and skip efficiency. */
    private const val DEFAULT_RETAIN_WINDOW_MS = 65

    fun toNormalizedAmplitude(thresholdDb: Float): Float {
        val clampedDb =
            if (thresholdDb.isFinite()) {
                thresholdDb.coerceIn(MIN_THRESHOLD_DB, MAX_THRESHOLD_DB)
            } else {
                DEFAULT_THRESHOLD_DB
            }
        return 10f.pow(clampedDb / 20f)
    }

    fun sanitizeMinSilenceMs(valueMs: Int): Int =
        if (valueMs <= 0) {
            DEFAULT_MIN_SILENCE_MS
        } else {
            valueMs.coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS)
        }

    /**
     * Sanitizes the retain-window duration in milliseconds.
     *
     * The retain window determines how many milliseconds of silence are kept
     * before speech resumes, preventing harsh clipping. Values outside the
     * 50–80 ms range are clamped; non-positive values fall back to the default.
     */
    fun sanitizeRetainWindowMs(valueMs: Int): Int =
        if (valueMs <= 0) {
            DEFAULT_RETAIN_WINDOW_MS
        } else {
            valueMs.coerceIn(MIN_RETAIN_WINDOW_MS, MAX_RETAIN_WINDOW_MS)
        }
}
