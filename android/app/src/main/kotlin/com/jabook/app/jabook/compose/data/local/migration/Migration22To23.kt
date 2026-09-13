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
 * Adds `topics_fts` FTS5 virtual table for instant offline search.
 *
 * Replaces `LIKE '%query%'` full-scan with `MATCH` + `bm25()` ranking.
 * Uses a contentless table to minimize index storage.
 * Triggers keep FTS in sync with cached_topics on INSERT/UPDATE/DELETE.
 * Tokenizer `unicode61 remove_diacritics 2` normalizes Latin diacritics.
 * Triggers use REPLACE(…, 'ё','е') to normalize Cyrillic ё→е at index time.
 */
public val MIGRATION_22_23: Migration =
    object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createTopicsFts5Index(db)
        }
    }

/**
 * Creates the RuTracker FTS index and its synchronization triggers.
 *
 * Room does not manage virtual FTS tables declared outside of entities. Therefore this helper
 * must be invoked by the 22→23 migration, on a fresh database, and on open to repair older
 * fresh installs that were created without the virtual table.
 */
public fun createTopicsFts5Index(db: SupportSQLiteDatabase) {
    val indexAlreadyExists = db.hasTable("topics_fts")

    try {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS topics_fts USING fts5(
                title, author,
                content='',
                tokenize = "unicode61 remove_diacritics 2"
            )
            """.trimIndent(),
        )
    } catch (error: SQLiteException) {
        if (error.isMissingFts5Module()) return
        throw error
    }

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS topics_fts_ai AFTER INSERT ON cached_topics BEGIN
            INSERT INTO topics_fts(rowid, title, author) VALUES (
                NEW.rowid,
                REPLACE(REPLACE(NEW.title, 'ё', 'е'), 'Ё', 'Е'),
                REPLACE(REPLACE(NEW.author, 'ё', 'е'), 'Ё', 'Е')
            );
        END
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS topics_fts_ad AFTER DELETE ON cached_topics BEGIN
            INSERT INTO topics_fts(topics_fts, rowid, title, author) VALUES (
                'delete', OLD.rowid,
                REPLACE(REPLACE(OLD.title, 'ё', 'е'), 'Ё', 'Е'),
                REPLACE(REPLACE(OLD.author, 'ё', 'е'), 'Ё', 'Е')
            );
        END
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS topics_fts_au AFTER UPDATE ON cached_topics BEGIN
            INSERT INTO topics_fts(topics_fts, rowid, title, author) VALUES (
                'delete', OLD.rowid,
                REPLACE(REPLACE(OLD.title, 'ё', 'е'), 'Ё', 'Е'),
                REPLACE(REPLACE(OLD.author, 'ё', 'е'), 'Ё', 'Е')
            );
            INSERT INTO topics_fts(rowid, title, author) VALUES (
                NEW.rowid,
                REPLACE(REPLACE(NEW.title, 'ё', 'е'), 'Ё', 'Е'),
                REPLACE(REPLACE(NEW.author, 'ё', 'е'), 'Ё', 'Е')
            );
        END
        """.trimIndent(),
    )

    if (!indexAlreadyExists) {
        db.execSQL(
            "INSERT INTO topics_fts(rowid, title, author) SELECT rowid, " +
                "REPLACE(REPLACE(title, 'ё', 'е'), 'Ё', 'Е'), " +
                "REPLACE(REPLACE(author, 'ё', 'е'), 'Ё', 'Е') FROM cached_topics",
        )
    }
}

internal fun SQLiteException.isMissingFts5Module(): Boolean = message?.contains("no such module: fts5", ignoreCase = true) == true

private fun SupportSQLiteDatabase.hasTable(tableName: String): Boolean =
    query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(tableName)).use {
        it.moveToFirst()
    }
