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
 * DSL helper to create Room migrations without boilerplate anonymous classes.
 *
 * Usage:
 * ```
 * val MIGRATION_22_23 = migration(22, 23) { db ->
 *     db.addColumn("books", "new_field", "TEXT")
 *     db.createIndex("books", "new_field")
 * }
 * ```
 */
public fun migration(
    from: Int,
    to: Int,
    body: (SupportSQLiteDatabase) -> Unit,
): Migration =
    object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) = body(db)
    }

/**
 * Extension to add a column to a table.
 *
 * Usage: `db.addColumn("books", "lufs_value", "REAL", default = "0.0")`
 */
public fun SupportSQLiteDatabase.addColumn(
    table: String,
    column: String,
    type: String,
    default: String? = null,
) {
    val defaultClause = default?.let { " DEFAULT $it" } ?: ""
    execSQL("ALTER TABLE $table ADD COLUMN $column $type$defaultClause")
}

/**
 * Check if a column exists in a table.
 *
 * Usage: `if (!db.hasColumn("chapters", "lufs_value")) { db.addColumn(...) }`
 */
public fun SupportSQLiteDatabase.hasColumn(
    table: String,
    column: String,
): Boolean {
    val cursor = query("PRAGMA table_info($table)")
    return cursor.use {
        while (it.moveToNext()) {
            if (it.getString(it.getColumnIndexOrThrow("name")) == column) return true
        }
        false
    }
}

/**
 * Extension to create an index.
 *
 * Usage: `db.createIndex("books", "title", "author")`
 */
public fun SupportSQLiteDatabase.createIndex(
    table: String,
    vararg columns: String,
) {
    val indexName = "idx_${table}_${columns.joinToString("_")}"
    execSQL("CREATE INDEX IF NOT EXISTS $indexName ON $table (${columns.joinToString(",")})")
}

/**
 * Extension to create an FTS5 virtual table with external content.
 *
 * Usage: `db.createFts5("topics_fts", "cached_topics", "title", "author")`
 */
public fun SupportSQLiteDatabase.createFts5(
    name: String,
    contentTable: String,
    vararg columns: String,
) {
    execSQL(
        "CREATE VIRTUAL TABLE IF NOT EXISTS $name USING fts5(" +
            "${columns.joinToString(", ")}, " +
            "content='$contentTable', content_rowid='rowid', " +
            "tokenize=\"unicode61 remove_diacritics 2\")",
    )
}
