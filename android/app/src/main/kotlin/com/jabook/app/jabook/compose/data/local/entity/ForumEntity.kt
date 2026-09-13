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

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * RuTracker forum display name cache, populated from the index.php category
 * tree by [com.jabook.app.jabook.compose.data.indexing.ForumCatalog]. Exists
 * purely to show real forum names in the UI instead of "Forum 574".
 */
@Keep
@Entity(
    tableName = "forums",
    indices = [
        androidx.room.Index(value = ["category_name"]),
    ],
)
public data class ForumEntity(
    @PrimaryKey
    @ColumnInfo(name = "forum_id")
    val forumId: String,
    @ColumnInfo(name = "name")
    val name: String,
    /** Parent category display name; blank for top-level categories. */
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)
