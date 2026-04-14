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

package com.jabook.app.jabook.compose.data.local.scanner

import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight duplicate detection policy for import/scan flows.
 *
 * BP-22.3 reference: detect likely duplicate books before creating new entries.
 */
public object BookDuplicateDetectionPolicy {
    public const val DEFAULT_DURATION_TOLERANCE_RATIO: Double = 0.03

    public data class Candidate(
        val title: String,
        val author: String?,
        val durationMs: Long?,
        val sourcePath: String?,
    )

    public fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    public fun areLikelyDuplicate(
        existing: Candidate,
        incoming: Candidate,
        durationToleranceRatio: Double = DEFAULT_DURATION_TOLERANCE_RATIO,
    ): Boolean {
        val titleMatches = normalize(existing.title) == normalize(incoming.title)
        if (!titleMatches) return false

        val existingAuthor = existing.author?.let(::normalize).orEmpty()
        val incomingAuthor = incoming.author?.let(::normalize).orEmpty()
        if (existingAuthor.isNotBlank() && incomingAuthor.isNotBlank() && existingAuthor != incomingAuthor) {
            return false
        }

        val existingPath = existing.sourcePath?.trim().orEmpty()
        val incomingPath = incoming.sourcePath?.trim().orEmpty()
        if (existingPath.isNotBlank() && incomingPath.isNotBlank() && existingPath == incomingPath) {
            return true
        }

        val existingDuration = existing.durationMs
        val incomingDuration = incoming.durationMs
        if (existingDuration == null || incomingDuration == null) {
            return true
        }

        val maxDuration = max(existingDuration, incomingDuration)
        val delta = abs(existingDuration - incomingDuration)
        val tolerance = (maxDuration * durationToleranceRatio).toLong()
        return delta <= tolerance
    }

    public fun findDuplicate(
        existing: List<Candidate>,
        incoming: Candidate,
    ): Candidate? = existing.firstOrNull { areLikelyDuplicate(it, incoming) }
}
