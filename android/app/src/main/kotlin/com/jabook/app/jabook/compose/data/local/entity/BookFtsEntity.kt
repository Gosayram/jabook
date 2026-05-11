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

package com.jabook.app.jabook.compose.data.local.entity

import androidx.room.Entity
import androidx.room.Fts5

/**
 * FTS5 mirror for books table.
 *
 * FTS5 provides better Unicode support, bm25() ranking, and prefix search vs FTS4.
 * Upgraded from FTS4 in DB version 21 (migration MIGRATION_20_21).
 */
@Fts5(contentEntity = BookEntity::class)
@Entity(tableName = "books_fts")
public data class BookFtsEntity(
    val title: String,
    val author: String,
    val description: String?,
)
