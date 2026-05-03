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

internal enum class ResumeRewindMode {
    FIXED,
    SMART,
}

internal object ResumeRewindPolicy {
    private const val FIVE_MINUTES_MS: Long = 5L * 60L * 1000L
    private const val THIRTY_MINUTES_MS: Long = 30L * 60L * 1000L
    private const val TWO_HOURS_MS: Long = 2L * 60L * 60L * 1000L
    private const val EIGHT_HOURS_MS: Long = 8L * 60L * 60L * 1000L
    private const val TWENTY_FOUR_HOURS_MS: Long = 24L * 60L * 60L * 1000L
    private val allowedSeconds: Set<Int> = setOf(0, 5, 10, 30)
    private const val SAFE_DEFAULT_SECONDS: Int = 10
    private const val MAX_SMART_SECONDS: Int = 120
    private const val SMART_STEP_SECONDS: Int = 5
    private const val MIN_AGGRESSIVENESS: Float = 0.5f
    private const val MAX_AGGRESSIVENESS: Float = 2.0f

    fun resolveRewindMs(
        pauseDurationMs: Long,
        configuredSeconds: Int,
        mode: ResumeRewindMode = ResumeRewindMode.FIXED,
        aggressiveness: Float = 1.0f,
    ): Long {
        val rewindSeconds =
            when (mode) {
                ResumeRewindMode.FIXED -> resolveFixedRewindSeconds(pauseDurationMs, configuredSeconds)
                ResumeRewindMode.SMART -> resolveSmartRewindSeconds(pauseDurationMs, aggressiveness)
            }
        return rewindSeconds * 1_000L
    }

    private fun resolveFixedRewindSeconds(
        pauseDurationMs: Long,
        configuredSeconds: Int,
    ): Long {
        if (pauseDurationMs <= THIRTY_MINUTES_MS) {
            return 0L
        }

        val safeSeconds = if (configuredSeconds in allowedSeconds) configuredSeconds else SAFE_DEFAULT_SECONDS
        return safeSeconds.toLong()
    }

    private fun resolveSmartRewindSeconds(
        pauseDurationMs: Long,
        aggressiveness: Float,
    ): Long {
        val baseSeconds =
            when {
                pauseDurationMs < FIVE_MINUTES_MS -> 0
                pauseDurationMs < THIRTY_MINUTES_MS -> 10
                pauseDurationMs < TWO_HOURS_MS -> 20
                pauseDurationMs < EIGHT_HOURS_MS -> 30
                pauseDurationMs < TWENTY_FOUR_HOURS_MS -> 45
                else -> 60
            }
        if (baseSeconds == 0) return 0L

        val safeAggressiveness = aggressiveness.coerceIn(MIN_AGGRESSIVENESS, MAX_AGGRESSIVENESS)
        val scaled = (baseSeconds * safeAggressiveness).toInt()
        val rounded = ((scaled + (SMART_STEP_SECONDS / 2)) / SMART_STEP_SECONDS) * SMART_STEP_SECONDS
        return rounded.coerceIn(0, MAX_SMART_SECONDS).toLong()
    }
}
