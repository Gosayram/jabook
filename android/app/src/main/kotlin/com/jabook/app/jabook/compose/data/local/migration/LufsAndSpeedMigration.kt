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
 * Migration from database version 18 to 19.
 *
 * Adds per-book audio analysis and preference columns:
 * - `lufs_value` (REAL, nullable): background loudness analysis result for the book.
 *   Used by [LufsLoudnessCompensationPolicy] to normalize volume between books.
 * - `preferred_speed` (REAL, nullable): per-book playback speed preference.
 *   When set, overrides the global speed setting for this specific book.
 */
public val MIGRATION_18_19: Migration =
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Idempotent: only add columns if they don't already exist.
            // This guards against edge cases where the schema already contains
            // these columns (e.g., during testing with version downgrades).
            val existing =
                db.query("PRAGMA table_info(books)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("lufs_value" !in existing) {
                db.execSQL("ALTER TABLE books ADD COLUMN lufs_value REAL DEFAULT NULL")
            }
            if ("preferred_speed" !in existing) {
                db.execSQL("ALTER TABLE books ADD COLUMN preferred_speed REAL DEFAULT NULL")
            }
        }
    }
