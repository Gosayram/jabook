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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test: v34→v35 migration crashed with SQLiteConstraintException(2067)
 * when pre-existing search history contained rows that normalize to the same key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration34To35Test {
    private fun createV34Db(): androidx.sqlite.db.SupportSQLiteDatabase {
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(34) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS search_history (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, query TEXT NOT NULL, " +
                                    "timestamp INTEGER NOT NULL, result_count INTEGER NOT NULL)",
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun insert(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        query: String,
        timestamp: Long,
    ): Long {
        val values =
            ContentValues().apply {
                put("query", query)
                put("timestamp", timestamp)
                put("result_count", 0)
            }
        return db.insert("search_history", SQLiteDatabase.CONFLICT_FAIL, values)
    }

    @Test
    fun `migration dedupes rows that normalize to the same key`() {
        val db = createV34Db()
        insert(db, "Foo", 1_000L)
        insert(db, "foo ", 2_000L)
        insert(db, "  FOO", 3_000L)
        insert(db, "Толкиен   двойными  пробелами", 4_000L)
        insert(db, "bar", 5_000L)

        MIGRATION_34_35.migrate(db)

        // Room's auto-generated index name must exist after migration.
        assertEquals(
            1,
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_search_history_normalized_query'").use {
                it.moveToFirst()
                it.getInt(0)
            },
        )

        // normalized_query must have NO default (Room schema validation is strict).
        assertEquals(
            0,
            db.query("PRAGMA table_info(search_history)").use { cursor ->
                var withDefault = 0
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "normalized_query") {
                        if (!cursor.isNull(4)) withDefault++
                    }
                }
                withDefault
            },
        )

        // "Foo"/"foo "/"  FOO" collapse to one row — the newest (id 3) survives.
        val rows =
            db.query("SELECT normalized_query FROM search_history ORDER BY normalized_query").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
        assertEquals(listOf("bar", "foo", "толкиен двойными пробелами"), rows)

        // No empty normalized keys left behind.
        assertTrue(
            db.query("SELECT COUNT(*) FROM search_history WHERE normalized_query = ''").use {
                it.moveToFirst()
                it.getInt(0) == 0
            },
        )
    }
}
