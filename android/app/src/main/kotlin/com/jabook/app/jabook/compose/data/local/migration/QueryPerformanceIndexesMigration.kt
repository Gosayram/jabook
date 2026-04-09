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
 * Migration from database version 17 to 18.
 *
 * Adds hot-path indices for:
 * - books ordering/filtering by favorite/recent/download/source/date
 * - chapter lookup by (book_id, chapter_index)
 */
public val MIGRATION_17_18: Migration =
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_is_favorite ON books(is_favorite)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_last_played_date ON books(last_played_date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_download_status ON books(download_status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_source_url ON books(source_url)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_books_added_date ON books(added_date)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chapters_book_id_chapter_index ON chapters(book_id, chapter_index)",
            )
        }
    }
