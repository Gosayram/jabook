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
 * Adds `normalized_position` column to bookmarks for re-scan-safe restore.
 *
 * Stores a 0..1 fraction of the chapter duration so a bookmark survives a library re-scan where
 * the absolute `position_ms` would otherwise point at the wrong offset in a re-encoded chapter.
 */
public val MIGRATION_28_29: Migration =
    object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing =
                db.query("PRAGMA table_info(bookmarks)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("normalized_position" !in existing) {
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN normalized_position REAL NOT NULL DEFAULT 0.0")
            }
        }
    }
