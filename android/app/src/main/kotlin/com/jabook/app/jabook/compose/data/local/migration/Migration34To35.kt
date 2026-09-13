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
 * Adds `normalized_query` to search_history for deduplication. Follows Room's
 * recreate-table recipe because schema validation is strict on two details:
 *
 * 1. The entity column has no DEFAULT — `ALTER TABLE ADD COLUMN ... DEFAULT ''`
 *    (required by SQLite for NOT NULL on a non-empty table) fails validation.
 *    Fix: backfill via a temp column, rebuild the table, drop the temp column
 *    with the table.
 * 2. Room expects the auto-generated index name `index_search_history_normalized_query`.
 *
 * Pre-existing "Foo" / "foo " / "  FOO" rows all normalize to the same key, so
 * dedup (newest id wins) runs BEFORE the unique index is created — otherwise
 * `CREATE UNIQUE INDEX` crashes with SQLiteConstraintException 2067.
 *
 * The SQL normalization mirrors the Kotlin runtime one
 * (`query.trim().replace(Regex("\\s+"), " ").lowercase()`): tab/CR/LF → space,
 * collapse space runs (the classic `~!`/`!~` trick), trim, lower.
 */
public val MIGRATION_34_35: Migration =
    Migration(34, 35) { db: SupportSQLiteDatabase ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`query` TEXT NOT NULL, " +
                "`normalized_query` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`result_count` INTEGER NOT NULL)",
        )
        // Temp normalized column on the old table (default required by SQLite for
        // NOT NULL ADD COLUMN); it disappears together with the old table.
        db.execSQL("ALTER TABLE search_history ADD COLUMN _tmp_normalized TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE search_history SET _tmp_normalized = LOWER(TRIM(" +
                "REPLACE(REPLACE(REPLACE(" +
                "REPLACE(REPLACE(REPLACE(query, char(9), ' '), char(10), ' '), char(13), ' '), " +
                "' ', '~!'), '!~', ''), '~!', ' ')" +
                "))",
        )
        // Keep the newest row per normalized key; must run BEFORE the unique index.
        db.execSQL(
            "DELETE FROM search_history WHERE id NOT IN " +
                "(SELECT MAX(id) FROM search_history GROUP BY _tmp_normalized)",
        )
        db.execSQL(
            "INSERT INTO `search_history_new` (`id`, `query`, `normalized_query`, `timestamp`, `result_count`) " +
                "SELECT `id`, `query`, `_tmp_normalized`, `timestamp`, `result_count` FROM `search_history`",
        )
        db.execSQL("DROP TABLE `search_history`")
        db.execSQL("ALTER TABLE `search_history_new` RENAME TO `search_history`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_normalized_query` ON `search_history` (`normalized_query`)")
    }
