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
 * Adds `lufs_value` column to chapters table for per-chapter loudness analysis.
 *
 * This allows [ChapterLoudnessTransitionPolicy] to normalize volume between
 * chapters of the same book when they have different recorded loudness levels.
 */
public val MIGRATION_25_26: Migration =
    object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing =
                db.query("PRAGMA table_info(chapters)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("lufs_value" !in existing) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN lufs_value REAL DEFAULT NULL")
            }
        }
    }
