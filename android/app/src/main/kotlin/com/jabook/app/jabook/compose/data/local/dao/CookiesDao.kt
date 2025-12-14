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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.compose.data.local.entity.CookieEntity

/**
 * DAO for cookie persistence.
 * Primary storage layer for authentication cookies.
 */
@Dao
interface CookiesDao {
    /**
     * Save cookies for a URL.
     * Replaces existing cookies if URL already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCookies(cookie: CookieEntity)

    /**
     * Get cookies for a specific URL.
     */
    @Query("SELECT * FROM cookies WHERE url = :url LIMIT 1")
    suspend fun getCookies(url: String): CookieEntity?

    /**
     * Get all cookies (for backup/debugging).
     */
    @Query("SELECT * FROM cookies")
    suspend fun getAllCookies(): List<CookieEntity>

    /**
     * Clear all cookies.
     */
    @Query("DELETE FROM cookies")
    suspend fun clearAllCookies()

    /**
     * Clear cookies for a specific URL.
     */
    @Query("DELETE FROM cookies WHERE url = :url")
    suspend fun clearCookies(url: String)
}
