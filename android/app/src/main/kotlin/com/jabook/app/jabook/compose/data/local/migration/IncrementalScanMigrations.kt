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
 * Migration from database version 16 to 17: adds `last_scan_timestamp` column
 * to the `scan_paths` table for incremental scan support.
 *
 * The new column defaults to `0` (never scanned), which triggers a full scan
 * on the first run after migration.
 */
public val MIGRATION_16_17: Migration =
    object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Idempotent check: skip if column already exists (e.g., fresh install at v17)
            val cursor = db.query("PRAGMA table_info(scan_paths)")
            val columnExists =
                cursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    generateSequence { if (it.moveToNext()) it.getString(nameIndex) else null }
                        .any { it == "last_scan_timestamp" }
                }
            if (!columnExists) {
                db.execSQL(
                    "ALTER TABLE scan_paths ADD COLUMN last_scan_timestamp INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
