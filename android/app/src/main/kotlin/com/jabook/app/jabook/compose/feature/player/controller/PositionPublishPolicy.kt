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

package com.jabook.app.jabook.compose.feature.player.controller

import kotlin.math.abs

internal object PositionPublishPolicy {
    private const val POSITION_UPDATE_EPSILON_MS: Long = 120L
    private const val OFFLOAD_UPDATE_EPSILON_MS: Long = 1000L

    fun shouldPublish(
        previousPositionMs: Long,
        incomingPositionMs: Long,
        force: Boolean,
        isAudioOffloaded: Boolean = false,
    ): Boolean {
        if (force) return true
        val epsilon = if (isAudioOffloaded) OFFLOAD_UPDATE_EPSILON_MS else POSITION_UPDATE_EPSILON_MS
        return abs(incomingPositionMs - previousPositionMs) >= epsilon
    }
}
