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
 * Adds `eq_preset_override` column to books table for per-book EQ preset override.
 *
 * This allows users to override the global EQ preset for individual books
 * that are poorly recorded and need a different equalization profile.
 */
public val MIGRATION_26_27: Migration =
    object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing =
                db.query("PRAGMA table_info(books)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("eq_preset_override" !in existing) {
                db.execSQL("ALTER TABLE books ADD COLUMN eq_preset_override TEXT DEFAULT NULL")
            }
        }
    }
