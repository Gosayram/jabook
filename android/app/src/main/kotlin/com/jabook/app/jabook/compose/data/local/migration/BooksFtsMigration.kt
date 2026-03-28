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

public val MIGRATION_15_16: Migration =
    object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS books_fts
                USING fts4(
                    title,
                    author,
                    description,
                    content='books',
                    tokenize=unicode61
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                INSERT INTO books_fts(rowid, title, author, description)
                SELECT rowid, title, author, description FROM books
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS books_ai
                AFTER INSERT ON books
                BEGIN
                    INSERT INTO books_fts(rowid, title, author, description)
                    VALUES (new.rowid, new.title, new.author, new.description);
                END
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS books_ad
                AFTER DELETE ON books
                BEGIN
                    INSERT INTO books_fts(books_fts, rowid, title, author, description)
                    VALUES ('delete', old.rowid, old.title, old.author, old.description);
                END
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS books_au
                AFTER UPDATE ON books
                BEGIN
                    INSERT INTO books_fts(books_fts, rowid, title, author, description)
                    VALUES ('delete', old.rowid, old.title, old.author, old.description);
                    INSERT INTO books_fts(rowid, title, author, description)
                    VALUES (new.rowid, new.title, new.author, new.description);
                END
                """.trimIndent(),
            )
        }
    }
