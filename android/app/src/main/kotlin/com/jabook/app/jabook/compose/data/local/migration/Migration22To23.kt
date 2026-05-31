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
 * Adds `topics_fts` FTS5 virtual table for instant offline search.
 *
 * Replaces `LIKE '%query%'` full-scan with `MATCH` + `bm25()` ranking.
 * Uses external-content table (`content='cached_topics'`) to avoid data duplication.
 * Triggers keep FTS in sync with cached_topics on INSERT/UPDATE/DELETE.
 * Tokenizer `unicode61 remove_diacritics 2` normalizes case, diacritics, and ё→е.
 */
public val MIGRATION_22_23: Migration =
    object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS topics_fts USING fts5(
                    title, author,
                    content='cached_topics', content_rowid='rowid',
                    tokenize = "unicode61 remove_diacritics 2"
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS topics_fts_ai AFTER INSERT ON cached_topics BEGIN
                    INSERT INTO topics_fts(rowid, title, author) VALUES (NEW.rowid, NEW.title, NEW.author);
                END
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS topics_fts_ad AFTER DELETE ON cached_topics BEGIN
                    INSERT INTO topics_fts(topics_fts, rowid, title, author) VALUES ('delete', OLD.rowid, OLD.title, OLD.author);
                END
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS topics_fts_au AFTER UPDATE ON cached_topics BEGIN
                    INSERT INTO topics_fts(topics_fts, rowid, title, author) VALUES ('delete', OLD.rowid, OLD.title, OLD.author);
                    INSERT INTO topics_fts(rowid, title, author) VALUES (NEW.rowid, NEW.title, NEW.author);
                END
                """.trimIndent(),
            )

            db.execSQL("INSERT INTO topics_fts(topics_fts) VALUES ('rebuild')")
        }
    }
