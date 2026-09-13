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

import android.database.sqlite.SQLiteException
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
 * Falls back to FTS4 if FTS5 is not available on the device.
 * Skips FTS creation entirely if neither is available.
 *
 * Steps:
 * 1. Drop old FTS4 triggers (created manually in MIGRATION_15_16)
 * 2. Drop old FTS4 virtual table and shadow tables
 * 3. Create new FTS5 (or FTS4 fallback) virtual table with same columns
 * 4. Repopulate from books table
 * 5. Recreate auto-sync triggers
 */
public val MIGRATION_20_21: Migration =
    object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS books_ai")
            db.execSQL("DROP TRIGGER IF EXISTS books_ad")
            db.execSQL("DROP TRIGGER IF EXISTS books_au")
            db.execSQL("DROP TABLE IF EXISTS books_fts")

            createBooksFtsIndex(db)
        }
    }

private data class FtsSupport(
    val fts4: Boolean,
    val fts5: Boolean,
)

private fun checkFtsSupport(db: SupportSQLiteDatabase): FtsSupport {
    var fts4 = false
    var fts5 = false
    try {
        val cursor = db.query("PRAGMA compile_options")
        cursor.use {
            while (it.moveToNext()) {
                val option = it.getString(0)
                if (option == "ENABLE_FTS3") fts4 = true
                if (option == "ENABLE_FTS5") fts5 = true
            }
        }
    } catch (_: Exception) {
        fts4 = true
    }
    return FtsSupport(fts4, fts5)
}

public fun createBooksFts5Index(db: SupportSQLiteDatabase) {
    createBooksFtsIndex(db)
}

public fun createBooksFtsIndex(db: SupportSQLiteDatabase) {
    val support = checkFtsSupport(db)

    if (!support.fts5 && !support.fts4) {
        return
    }

    val useFts5 = support.fts5
    val module = if (useFts5) "fts5" else "fts4"
    val tokenize = if (useFts5) "tokenize='unicode61'" else "tokenize=unicode61"

    try {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS books_fts
            USING $module(
                title,
                author,
                description,
                content='books',
                content_rowid='rowid',
                $tokenize
            )
            """.trimIndent(),
        )
    } catch (error: SQLiteException) {
        if (useFts5 && error.isMissingFts5Module()) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS books_fts
                USING fts4(
                    title,
                    author,
                    description,
                    content='books',
                    content_rowid='rowid',
                    tokenize=unicode61
                )
                """.trimIndent(),
            )
        } else {
            throw error
        }
    }

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
