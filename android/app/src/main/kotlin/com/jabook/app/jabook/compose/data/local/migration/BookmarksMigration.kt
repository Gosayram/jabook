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
 * Migration 19 -> 20: add bookmarks table for timeline bookmarks and voice-note metadata.
 */
public val MIGRATION_19_20: Migration =
    object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bookmarks` (
                    `id` TEXT NOT NULL,
                    `book_id` TEXT NOT NULL,
                    `chapter_index` INTEGER NOT NULL,
                    `position_ms` INTEGER NOT NULL,
                    `note_text` TEXT,
                    `note_audio_path` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_book_id` ON `bookmarks` (`book_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_book_id_position_ms` ON `bookmarks` (`book_id`, `position_ms`)",
            )
        }
    }
