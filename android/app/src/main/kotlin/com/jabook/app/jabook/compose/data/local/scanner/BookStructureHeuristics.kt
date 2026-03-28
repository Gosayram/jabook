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

internal enum class BookStructureType {
    SINGLE_FILE,
    NUMBERED_FILES,
    NESTED_SERIES,
    UNKNOWN,
}

internal object BookStructureHeuristics {
    private const val LARGE_SINGLE_FILE_MIN_DURATION_MS = 30 * 60 * 1000L
    private const val MIN_NUMBERED_FILES = 3
    private val numberedPrefixRegex = Regex("""^\s*(\d{1,3})[\s._-].*""")

    fun classify(
        fileNames: List<String>,
        hasNestedDirectories: Boolean,
        singleFileDurationMs: Long?,
    ): BookStructureType {
        if (hasNestedDirectories) {
            return BookStructureType.NESTED_SERIES
        }

        if (fileNames.size == 1) {
            if ((singleFileDurationMs ?: 0L) >= LARGE_SINGLE_FILE_MIN_DURATION_MS) {
                return BookStructureType.SINGLE_FILE
            }
            return BookStructureType.UNKNOWN
        }

        val numberedCount = fileNames.count { numberedPrefixRegex.matches(it) }
        if (fileNames.size >= MIN_NUMBERED_FILES && numberedCount >= MIN_NUMBERED_FILES) {
            return BookStructureType.NUMBERED_FILES
        }

        return BookStructureType.UNKNOWN
    }
}
