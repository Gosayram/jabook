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

/**
 * Aligns skip-silence boundaries with the device output buffer.
 *
 * A silence transition in the middle of an output buffer can produce an audible boundary on
 * devices with small low-latency buffers. The platform buffer size is resolved when the player
 * is built and passed as a value; this policy deliberately has no Android dependency and is not
 * invoked from the PCM processing loop.
 */
internal object SkipSilenceBufferPolicy {
    fun alignMinimumSilenceFrames(
        configuredFrames: Int,
        outputFramesPerBuffer: Int?,
    ): Int {
        val minimumFrames = configuredFrames.coerceAtLeast(1)
        val bufferFrames = outputFramesPerBuffer?.takeIf { it > 0 } ?: return minimumFrames

        return ((minimumFrames.toLong() + bufferFrames - 1L) / bufferFrames)
            .times(bufferFrames)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }
}
