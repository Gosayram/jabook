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
 * Adds `start_position_ms`/`end_position_ms` columns to chapters for embedded
 * M4B/MP4 chapter offsets.
 *
 * NULL (the default) keeps the previous meaning: the chapter spans its whole file,
 * so every existing row stays valid.
 */
public val MIGRATION_29_30: Migration =
    object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val existing =
                db.query("PRAGMA table_info(chapters)").use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            if ("start_position_ms" !in existing) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN start_position_ms INTEGER")
            }
            if ("end_position_ms" !in existing) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN end_position_ms INTEGER")
            }
            // ponytail: drop dead cookies table (replaced by PersistentCookieJar)
            db.execSQL("DROP TABLE IF EXISTS cookies")
        }
    }
