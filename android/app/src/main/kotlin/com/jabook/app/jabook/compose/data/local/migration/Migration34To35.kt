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

package com.jabook.app.jabook.compose.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 34 to 35.
 *
 * Adds normalized_query column to search_history for deduplication, backfills
 * existing rows, deduplicates (pre-existing "foo" / "foo " / "FOO" rows all
 * normalize to the same key — creating the unique index before dedup crashed
 * with SQLiteConstraintException 2067), then creates the unique index.
 *
 * The SQL normalization mirrors the Kotlin runtime one
 * (`query.trim().replace(Regex("\\s+"), " ").lowercase()`): tab/CR/LF → space,
 * collapse space runs (the classic `~!`/`!~` trick), trim, lower.
 */
public val MIGRATION_34_35: Migration =
    Migration(34, 35) { db: SupportSQLiteDatabase ->
        db.execSQL("ALTER TABLE search_history ADD COLUMN normalized_query TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE search_history SET normalized_query = LOWER(TRIM(" +
                "REPLACE(REPLACE(REPLACE(" +
                "REPLACE(REPLACE(REPLACE(query, char(9), ' '), char(10), ' '), char(13), ' '), " +
                "' ', '~!'), '!~', ''), '~!', ' ')" +
                ")) WHERE normalized_query = ''",
        )
        // Keep the newest row per normalized key; must run BEFORE the unique index.
        db.execSQL(
            "DELETE FROM search_history WHERE id NOT IN " +
                "(SELECT MAX(id) FROM search_history GROUP BY normalized_query)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_search_history_normalized_query ON search_history (normalized_query)")
    }
