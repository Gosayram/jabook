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
 * Adds normalized_query column to search_history for deduplication,
 * backfills existing rows, and creates a unique index.
 */
public val MIGRATION_34_35: Migration =
    Migration(34, 35) { db: SupportSQLiteDatabase ->
        db.execSQL("ALTER TABLE search_history ADD COLUMN normalized_query TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE search_history SET normalized_query = LOWER(TRIM(REPLACE(query, '  ', ' '))) WHERE normalized_query = ''",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_search_history_normalized_query ON search_history (normalized_query)")
    }
