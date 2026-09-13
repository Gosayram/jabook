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
 * Scopes the books_fts UPDATE trigger to only the FTS-indexed columns
 * (title, author, description). Previously it fired on EVERY books row
 * update — including the 5-second playback-progress save — rewriting the
 * FTS entry for the playing book even though its searchable text was
 * unchanged (continuous search-index churn on large libraries).
 */
public val MIGRATION_32_33: Migration =
    object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS books_au")
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS books_au
                AFTER UPDATE OF title, author, description ON books BEGIN
                    INSERT INTO books_fts(books_fts, rowid, title, author, description)
                    VALUES ('delete', old.rowid, old.title, old.author, COALESCE(old.description, ''));
                    INSERT INTO books_fts(rowid, title, author, description)
                    VALUES (new.rowid, new.title, new.author, COALESCE(new.description, ''));
                END
                """.trimIndent(),
            )
        }
    }
