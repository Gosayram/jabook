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

package com.jabook.app.jabook.compose.data.model

/**
 * Sort order options for library books.
 */
enum class BookSortOrder {
    /** Sort by last activity (most recently played first) */
    BY_ACTIVITY,

    /** Sort by title ascending (A-Z, А-Я) */
    TITLE_ASC,

    /** Sort by title descending (Z-A, Я-А) */
    TITLE_DESC,

    /** Sort by author ascending (A-Z, А-Я) */
    AUTHOR_ASC,

    /** Sort by author descending (Z-A, Я-А) */
    AUTHOR_DESC,

    /** Sort by date added (newest first) */
    RECENTLY_ADDED,

    /** Sort by date added (oldest first) */
    OLDEST_FIRST,
}
