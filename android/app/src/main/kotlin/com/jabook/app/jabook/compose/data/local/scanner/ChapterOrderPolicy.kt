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

internal data class ChapterOrderCandidate(
    val displayName: String,
    val trackNumber: Int?,
)

internal object ChapterOrderPolicy {
    private val leadingNumberRegex = Regex("""^\s*(\d{1,3})[\s._-]?.*""")

    fun comparator(): Comparator<ChapterOrderCandidate> =
        compareBy<ChapterOrderCandidate>(
            { candidate -> priority(candidate) },
            { candidate -> trackSortKey(candidate) },
            { candidate -> filenameNumericSortKey(candidate) },
            { candidate -> candidate.displayName.lowercase() },
        )

    private fun priority(candidate: ChapterOrderCandidate): Int =
        when {
            (candidate.trackNumber ?: 0) > 0 -> 0
            extractLeadingNumber(candidate.displayName) != null -> 1
            else -> 2
        }

    private fun trackSortKey(candidate: ChapterOrderCandidate): Int = candidate.trackNumber ?: Int.MAX_VALUE

    private fun filenameNumericSortKey(candidate: ChapterOrderCandidate): Int = extractLeadingNumber(candidate.displayName) ?: Int.MAX_VALUE

    private fun extractLeadingNumber(fileName: String): Int? =
        leadingNumberRegex
            .find(fileName)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
}
