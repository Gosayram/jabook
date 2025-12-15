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

package com.jabook.app.jabook.compose.domain.model

/**
 * Download priority levels.
 * Higher priority downloads are processed first.
 */
enum class DownloadPriority(
    val value: Int,
) {
    /**
     * Low priority - process only when no other downloads are active.
     */
    LOW(0),

    /**
     * Normal priority - default for most downloads.
     */
    NORMAL(1),

    /**
     * High priority - process before normal downloads.
     */
    HIGH(2),

    /**
     * Urgent priority - process immediately, highest priority.
     */
    URGENT(3),
    ;

    companion object {
        /**
         * Get priority from integer value.
         * Returns NORMAL if value is invalid.
         */
        fun fromValue(value: Int): DownloadPriority = entries.firstOrNull { it.value == value } ?: NORMAL
    }
}
