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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Exhaustive pairwise migration matrix (spotube-style): for every exported
 * schema version N (30..34), build a real DB at vN, run all migrations up to
 * v35, and validate the result against the exported 35.json schema.
 *
 * Coverage is bounded by exported schemas: android/app/schemas/ only contains
 * JabookDatabase 30-35.json. Add 29.json (and older) to extend the matrix.
 *
 * The v34→v35 hop additionally runs the data-integrity invariants (dedup of
 * duplicate normalized search queries, preservation of distinct rows) from
 * Migration34To35Test — folded into the matrix instead of duplicated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JabookMigrationMatrixTest {
    private val migrations: List<Pair<Int, Migration>> =
        listOf(
            30 to MIGRATION_30_31,
            31 to MIGRATION_31_32,
            32 to MIGRATION_32_33,
            33 to MIGRATION_33_34,
            34 to MIGRATION_34_35,
        )

    @Test
    fun `migrate from v30 to v35`() = matrix(30)

    @Test
    fun `migrate from v31 to v35`() = matrix(31)

    @Test
    fun `migrate from v32 to v35`() = matrix(32)

    @Test
    fun `migrate from v33 to v35`() = matrix(33)

    @Test
    fun `migrate from v34 to v35 preserves and dedupes search history`() = matrix(34, seedSearchHistory = true)

    private fun matrix(
        startVersion: Int,
        seedSearchHistory: Boolean = false,
    ) {
        val db = createSchemaAt(startVersion)
        try {
            if (seedSearchHistory) seedSearchHistory(db)
            migrations
                .filter { (from, _) -> from >= startVersion }
                .sortedBy { (from, _) -> from }
                .forEach { (_, migration) -> migration.migrate(db) }
            db.version = 35
            validateAgainst(db, version = 35)
            if (seedSearchHistory) assertSearchHistoryInvariants(db)
        } finally {
            db.close()
        }
    }

    /**
     * Builds a DB at [version] from the exported Room schema JSON — the same
     * recipe androidx.room:room-testing's MigrationTestHelper uses internally
     * (entity createSql + index createSql with ${TABLE_NAME} substitution),
     * minus the room-testing dependency.
     */
    private fun createSchemaAt(version: Int): SupportSQLiteDatabase {
        val schema = readSchema(version)
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val entities = schema.getJSONObject("database").getJSONArray("entities")
                            for (i in 0 until entities.length()) {
                                val entity = entities.getJSONObject(i)
                                val table = entity.getString("tableName")
                                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                                val indices = entity.optJSONArray("indices")
                                if (indices != null) {
                                    for (j in 0 until indices.length()) {
                                        db.execSQL(
                                            indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table),
                                        )
                                    }
                                }
                            }
                            // Room does not track the manual books FTS index and its
                            // triggers (created pre-v15, recreated by migrations);
                            // MIGRATION_32_33 recreates books_au and needs the table.
                            createBooksFtsIndex(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    /** Validates every entity (tables, columns, indices) of the exported schema. */
    private fun validateAgainst(
        db: SupportSQLiteDatabase,
        version: Int,
    ) {
        val schema = readSchema(version)
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")

            assertTrue(
                "table `$table` missing after migration to v$version",
                tableExists(db, table),
            )

            val fields = entity.getJSONArray("fields")
            for (f in 0 until fields.length()) {
                val column = fields.getJSONObject(f).getString("columnName")
                assertTrue(
                    "column `$table`.`$column` missing after migration to v$version",
                    columnExists(db, table, column),
                )
            }

            val indices = entity.optJSONArray("indices")
            if (indices != null) {
                for (j in 0 until indices.length()) {
                    val indexName = indices.getJSONObject(j).getString("name")
                    assertTrue(
                        "index `$indexName` missing after migration to v$version",
                        indexExists(db, indexName),
                    )
                }
            }
        }
    }

    private fun seedSearchHistory(db: SupportSQLiteDatabase) {
        // Same rows as Migration34To35Test plus a tab/newline edge case. ASCII-only
        // expectations: SQLite LOWER() is ASCII-only, so Cyrillic case is not asserted.
        insertHistory(db, "Foo", 1_000L)
        insertHistory(db, "foo ", 2_000L)
        insertHistory(db, "  FOO", 3_000L)
        insertHistory(db, "Толкиен   двойными  пробелами", 4_000L)
        insertHistory(db, "bar", 5_000L)
        insertHistory(db, "A\tB\nC", 6_000L)
    }

    private fun insertHistory(
        db: SupportSQLiteDatabase,
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

    private fun assertSearchHistoryInvariants(db: SupportSQLiteDatabase) {
        // Room's auto-generated unique index must exist after migration.
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
                    if (cursor.getString(1) == "normalized_query" && !cursor.isNull(4)) withDefault++
                }
                withDefault
            },
        )

        // "Foo"/"foo "/"  FOO" collapse to exactly one row — the newest (id 3) survives.
        db.query("SELECT id, query FROM search_history WHERE normalized_query = 'foo'").use { cursor ->
            assertTrue("deduped 'foo' row missing", cursor.moveToFirst())
            assertEquals(3L, cursor.getLong(0))
            assertEquals("  FOO", cursor.getString(1))
            assertFalse("duplicate 'foo' rows survived", cursor.moveToNext())
        }

        // Whitespace variants collapse; the original query text is preserved verbatim.
        db.query("SELECT query FROM search_history WHERE normalized_query = 'a b c'").use { cursor ->
            assertTrue("tab/newline edge-case row missing", cursor.moveToFirst())
            assertEquals("A\tB\nC", cursor.getString(0))
        }

        // Distinct rows survive untouched.
        assertEquals(
            1,
            db.query("SELECT COUNT(*) FROM search_history WHERE query = 'bar'").use {
                it.moveToFirst()
                it.getInt(0)
            },
        )
        assertEquals(
            1,
            db.query("SELECT COUNT(*) FROM search_history WHERE query = 'Толкиен   двойными  пробелами'").use {
                it.moveToFirst()
                it.getInt(0)
            },
        )

        // No empty normalized keys, no duplicate normalized groups.
        assertEquals(
            0,
            db.query("SELECT COUNT(*) FROM search_history WHERE normalized_query = ''").use {
                it.moveToFirst()
                it.getInt(0)
            },
        )
        assertEquals(
            0,
            db
                .query(
                    "SELECT COUNT(*) FROM (SELECT normalized_query FROM search_history " +
                        "GROUP BY normalized_query HAVING COUNT(*) > 1)",
                ).use {
                    it.moveToFirst()
                    it.getInt(0)
                },
        )
    }

    private fun readSchema(version: Int): JSONObject = JSONObject(schemaFile(version).readText())

    private fun schemaFile(version: Int): File {
        val relativeDir = "schemas/com.jabook.app.jabook.compose.data.local.JabookDatabase"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val base = dir ?: return@repeat
            val hit =
                listOf(base, File(base, "app"), File(File(base, "android"), "app"))
                    .map { File(File(it, relativeDir), "$version.json") }
                    .firstOrNull { it.isFile }
            if (hit != null) return hit
            dir = base.parentFile
        }
        error(
            "Schema $version.json not found under $relativeDir — " +
                "commit the exported schema to enable matrix coverage from that version",
        )
    }

    private fun tableExists(
        db: SupportSQLiteDatabase,
        table: String,
    ): Boolean =
        count(
            db,
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table'",
        ) > 0

    private fun indexExists(
        db: SupportSQLiteDatabase,
        index: String,
    ): Boolean =
        count(
            db,
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='$index'",
        ) > 0

    private fun columnExists(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
    ): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return true
            }
            false
        }

    private fun count(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Int =
        db.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }
}
