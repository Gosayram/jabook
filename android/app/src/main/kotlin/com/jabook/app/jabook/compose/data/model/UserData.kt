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

package com.jabook.app.jabook.compose.data.model

/**
 * Domain model for user preferences and settings.
 *
 * This is stored in DataStore and exposed via UserPreferencesRepository.
 */
public data class UserData(
    public val theme: AppTheme = AppTheme.SYSTEM,
    public val sortOrder: BookSortOrder = BookSortOrder.BY_ACTIVITY,
    public val viewMode: LibraryViewMode = LibraryViewMode.LIST_COMPACT,
    public val autoPlayNext: Boolean = true,
    public val playbackSpeed: Float = 1.0f,
    public val font: AppFont = AppFont.DEFAULT,
    public val normalizeChapterTitles: Boolean = false, // Default: OFF
    public val onboardingCompleted: Boolean = false,
)

/**
 * App theme options.
 */
public enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM,
    AMOLED,
}

/**
 * Library view mode options.
 */
public enum class LibraryViewMode {
    /** Compact list view */
    LIST_COMPACT,

    /** Grid view - compact (3 cols phone, 6 tablet) */
    GRID_COMPACT,

    /** Grid view - comfortable (2 cols phone, 4 tablet) */
    GRID_COMFORTABLE,
}
