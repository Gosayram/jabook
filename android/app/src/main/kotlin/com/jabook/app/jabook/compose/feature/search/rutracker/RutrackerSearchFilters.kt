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

package com.jabook.app.jabook.compose.feature.search.rutracker

/**
 * Sorting options for RuTracker search results.
 */
enum class RutrackerSortOrder {
    /** Sort by relevance (default order from RuTracker) */
    RELEVANCE,

    /** Sort by number of seeders (descending) */
    SEEDERS_DESC,

    /** Sort by file size (descending) */
    SIZE_DESC,

    /** Sort by file size (ascending) */
    SIZE_ASC,

    /** Sort by title (A-Z) */
    TITLE_ASC,

    /** Sort by title (Z-A) */
    TITLE_DESC,
}

/**
 * Filter criteria for RuTracker search.
 *
 * @param minSeeders Minimum number of seeders (null = no filter)
 * @param minSizeMb Minimum file size in MB (null = no filter)
 * @param maxSizeMb Maximum file size in MB (null = no filter)
 */
data class RutrackerSearchFilters(
    val minSeeders: Int? = null,
    val minSizeMb: Int? = null,
    val maxSizeMb: Int? = null,
) {
    /** Check if any filters are active */
    fun isActive(): Boolean = minSeeders != null || minSizeMb != null || maxSizeMb != null

    /** Clear all filters */
    fun clear(): RutrackerSearchFilters = RutrackerSearchFilters()
}
