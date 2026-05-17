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

internal object ChapterDetectionEnqueueGuardPolicy {
    internal const val SAME_SIGNATURE_DEBOUNCE_MS: Long = 5L * 60L * 1000L

    internal data class FileSignature(
        val filePath: String,
        val fileIndex: Int,
        val durationMs: Long,
        val lastModifiedMs: Long,
    )

    internal data class EnqueueRecord(
        val signature: FileSignature,
        val enqueuedAtMs: Long,
    )

    internal fun shouldSkipEnqueue(
        previous: EnqueueRecord?,
        next: FileSignature,
        nowMs: Long,
    ): Boolean {
        if (previous == null) return false
        if (previous.signature != next) return false
        val elapsed = (nowMs - previous.enqueuedAtMs).coerceAtLeast(0L)
        return elapsed < SAME_SIGNATURE_DEBOUNCE_MS
    }
}
