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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.UserData
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user preferences.
 *
 * Provides access to app settings and user preferences stored in DataStore.
 */
interface UserPreferencesRepository {
    /**
     * Get user preferences as a Flow.
     * Emits whenever preferences change.
     */
    val userData: Flow<UserData>

    /**
     * Update app theme preference.
     */
    suspend fun setTheme(theme: AppTheme)

    /**
     * Update book sort order preference.
     */
    suspend fun setSortOrder(sortOrder: BookSortOrder)

    /**
     * Update auto-play next preference.
     */
    suspend fun setAutoPlayNext(enabled: Boolean)

    /**
     * Update playback speed preference.
     */
    suspend fun setPlaybackSpeed(speed: Float)
}
