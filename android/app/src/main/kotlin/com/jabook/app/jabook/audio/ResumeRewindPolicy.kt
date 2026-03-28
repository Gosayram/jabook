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

internal object ResumeRewindPolicy {
    private const val LONG_PAUSE_THRESHOLD_MS: Long = 30L * 60L * 1000L
    private val allowedSeconds: Set<Int> = setOf(0, 5, 10, 30)

    fun resolveRewindMs(
        pauseDurationMs: Long,
        configuredSeconds: Int,
    ): Long {
        if (pauseDurationMs <= LONG_PAUSE_THRESHOLD_MS) {
            return 0L
        }

        val safeSeconds = if (configuredSeconds in allowedSeconds) configuredSeconds else 10
        return safeSeconds * 1000L
    }
}
