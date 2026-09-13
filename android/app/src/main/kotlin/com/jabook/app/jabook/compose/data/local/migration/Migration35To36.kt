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
 * Migration from database version 35 to 36.
 *
 * Heals users who already committed the BROKEN v35 search_history (pre-fix
 * [MIGRATION_34_35]): `normalized_query` with `DEFAULT ''` and an index named
 * `idx_search_history_normalized_query`. They sit at version 35, so no
 * migration ever runs again and Room schema validation crashes on every open.
 * The rebuild is unconditional — it is idempotent for healthy v35 databases.
 *
 * Steps, in collision-safe order:
 * 1. Recompute empty/NULL normalized keys from the original query (broken
 *    rows may carry `''`; the SQL mirrors the Kotlin runtime normalization,
 *    same as [MIGRATION_34_35]).
 * 2. Dedup keeping MAX(id) per final key — must happen BEFORE the unique
 *    index, otherwise healing two `''` rows into the same key would crash
 *    `CREATE UNIQUE INDEX` with SQLiteConstraintException 2067.
 * 3. Rebuild the table to the exact entity schema (no column DEFAULTs) and
 *    recreate Room's auto-generated index name
 *    `index_search_history_normalized_query`.
 *
 * Dropping duplicate history rows is acceptable.
 */
public val MIGRATION_35_36: Migration =
    Migration(35, 36) { db: SupportSQLiteDatabase ->
        // 1. Heal empty/NULL keys from the original query text.
        db.execSQL(
            "UPDATE search_history SET normalized_query = LOWER(TRIM(" +
                "REPLACE(REPLACE(REPLACE(" +
                "REPLACE(REPLACE(REPLACE(query, char(9), ' '), char(10), ' '), char(13), ' '), " +
                "' ', '~!'), '!~', ''), '~!', ' ')" +
                ")) " +
                "WHERE normalized_query IS NULL OR TRIM(normalized_query) = ''",
        )
        // 2. Dedup on the FINAL keys (newest id wins) before the unique index.
        db.execSQL(
            "DELETE FROM search_history WHERE id NOT IN " +
                "(SELECT MAX(id) FROM search_history GROUP BY normalized_query)",
        )
        // 3. Rebuild to the exact entity schema (no DEFAULTs).
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`query` TEXT NOT NULL, " +
                "`normalized_query` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`result_count` INTEGER NOT NULL)",
        )
        db.execSQL("DROP INDEX IF EXISTS `idx_search_history_normalized_query`")
        db.execSQL(
            "INSERT INTO `search_history_new` (`id`, `query`, `normalized_query`, `timestamp`, `result_count`) " +
                "SELECT `id`, `query`, `normalized_query`, `timestamp`, `result_count` FROM `search_history`",
        )
        db.execSQL("DROP TABLE `search_history`")
        db.execSQL("ALTER TABLE `search_history_new` RENAME TO `search_history`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_search_history_normalized_query` ON `search_history` (`normalized_query`)")
    }
