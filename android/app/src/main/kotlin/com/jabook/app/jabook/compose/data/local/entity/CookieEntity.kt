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

package com.jabook.app.jabook.compose.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for persisting authentication cookies in database.
 * Provides most reliable storage layer for session cookies.
 */
@Entity(tableName = "cookies")
data class CookieEntity(
    @PrimaryKey
    val url: String,
    val cookieHeader: String,
    val timestamp: Long = System.currentTimeMillis(),
)
