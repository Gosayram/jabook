// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.audio.core.model

/**
 * Represents a chapter in an audiobook.
 */
data class Chapter(
    val id: String,
    val title: String,
    val fileIndex: Int,
    val filePath: String? = null,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val duration: Long? = null,
) {
    /**
     * Returns the duration in milliseconds.
     * If duration is not set, calculates it from startTime and endTime.
     */
    fun getDurationMs(): Long? = duration ?: endTime?.let { it - startTime }

    /**
     * Checks if the chapter has a valid file path.
     */
    fun hasFilePath(): Boolean = !filePath.isNullOrBlank()
}
