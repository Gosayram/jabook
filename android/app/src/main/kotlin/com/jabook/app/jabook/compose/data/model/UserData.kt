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
 * Domain model for user preferences and settings.
 *
 * This is stored in DataStore and exposed via UserPreferencesRepository.
 */
data class UserData(
    val theme: AppTheme = AppTheme.SYSTEM,
    val sortOrder: BookSortOrder = BookSortOrder.RECENTLY_PLAYED,
    val autoPlayNext: Boolean = true,
    val playbackSpeed: Float = 1.0f,
)

/**
 * App theme options.
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * Book sorting options for Library screen.
 */
enum class BookSortOrder {
    RECENTLY_PLAYED,
    RECENTLY_ADDED,
    TITLE_ASC,
    TITLE_DESC,
    AUTHOR_ASC,
    AUTHOR_DESC,
}
