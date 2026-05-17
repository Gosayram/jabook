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
 * Migrates the books FTS index from FTS4 to FTS5.
 *
 * FTS5 provides:
 * - bm25() relevance ranking for better search results
 * - Better Unicode / prefix search support
 * - Improved performance on large datasets
 *
 * Steps:
 * 1. Drop old FTS4 triggers (created manually in MIGRATION_15_16)
 * 2. Drop old FTS4 virtual table and shadow tables
 * 3. Create new FTS5 virtual table with same columns
 * 4. Repopulate from books table
 * 5. Recreate auto-sync triggers for FTS5
 */
public val MIGRATION_20_21: Migration =
    object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop old FTS4 triggers
            db.execSQL("DROP TRIGGER IF EXISTS books_ai")
            db.execSQL("DROP TRIGGER IF EXISTS books_ad")
            db.execSQL("DROP TRIGGER IF EXISTS books_au")

            // Drop old FTS4 virtual table (also drops shadow tables: _content, _segdir, _segments, _stat)
            db.execSQL("DROP TABLE IF EXISTS books_fts")

            createBooksFts5Index(db)
        }
    }

public fun createBooksFts5Index(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS books_fts
        USING fts5(
            title,
            author,
            description,
            content='books',
            content_rowid='rowid',
            tokenize='unicode61'
        )
        """.trimIndent(),
    )

    db.execSQL(
        """
        INSERT INTO books_fts(rowid, title, author, description)
        SELECT rowid, title, author, COALESCE(description, '') FROM books
        WHERE NOT EXISTS (SELECT 1 FROM books_fts LIMIT 1)
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS books_ai
        AFTER INSERT ON books BEGIN
            INSERT INTO books_fts(rowid, title, author, description)
            VALUES (new.rowid, new.title, new.author, COALESCE(new.description, ''));
        END
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS books_ad
        AFTER DELETE ON books BEGIN
            INSERT INTO books_fts(books_fts, rowid, title, author, description)
            VALUES ('delete', old.rowid, old.title, old.author, COALESCE(old.description, ''));
        END
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS books_au
        AFTER UPDATE ON books BEGIN
            INSERT INTO books_fts(books_fts, rowid, title, author, description)
            VALUES ('delete', old.rowid, old.title, old.author, COALESCE(old.description, ''));
            INSERT INTO books_fts(rowid, title, author, description)
            VALUES (new.rowid, new.title, new.author, COALESCE(new.description, ''));
        END
        """.trimIndent(),
    )
}
