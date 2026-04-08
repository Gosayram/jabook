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

package com.jabook.app.jabook.compose.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseModuleMigrationSmokeTest {
    private lateinit var context: Context
    private var database: JabookDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DatabaseModule.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DatabaseModule.DATABASE_NAME)
    }

    @Test
    fun `database initializes at version 17 with required tables`() {
        database = DatabaseModule.provideJabookDatabase(context)
        val sqlDb = requireNotNull(database).openHelper.writableDatabase

        assertEquals(17, pragmaUserVersion(sqlDb))
        assertTrue(tableExists(sqlDb, "books"))
        assertTrue(tableExists(sqlDb, "chapters"))
        assertTrue(tableExists(sqlDb, "cached_topics"))
        assertTrue(tableExists(sqlDb, "books_fts"))
    }

    @Test
    fun `migration contract includes 14 to 15 and 15 to 16 and 16 to 17 and migration updates blank category`() {
        val migrationPairs = DatabaseModule.configuredMigrations.map { it.startVersion to it.endVersion }
        assertTrue(migrationPairs.contains(14 to 15))
        assertTrue(migrationPairs.contains(15 to 16))
        assertTrue(migrationPairs.contains(16 to 17))

        database = DatabaseModule.provideJabookDatabase(context)
        val initialSqlDb = requireNotNull(database).openHelper.writableDatabase

        initialSqlDb.execSQL(
            """
            INSERT INTO cached_topics(
                topic_id,
                title,
                author,
                category,
                size,
                seeders,
                leechers,
                magnet_url,
                torrent_url,
                cover_url,
                timestamp,
                last_updated,
                index_version
            ) VALUES (
                'topic-migration-smoke',
                'Migration Smoke Title',
                'Migration Smoke Author',
                '',
                '1 GB',
                5,
                1,
                NULL,
                NULL,
                NULL,
                1000,
                1000,
                1
            )
            """.trimIndent(),
        )
        requireNotNull(database).close()
        database = null

        val dbPath = context.getDatabasePath(DatabaseModule.DATABASE_NAME).absolutePath
        SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE).use { rawDb ->
            rawDb.execSQL("PRAGMA user_version = 14")
        }

        database = DatabaseModule.provideJabookDatabase(context)
        val migratedSqlDb = requireNotNull(database).openHelper.writableDatabase

        assertEquals(17, pragmaUserVersion(migratedSqlDb))
        assertEquals(
            "Аудиокниги",
            querySingleString(
                migratedSqlDb,
                """
                SELECT category
                FROM cached_topics
                WHERE topic_id = 'topic-migration-smoke'
                """.trimIndent(),
            ),
        )
    }

    private fun tableExists(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): Boolean =
        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'").use { cursor ->
            cursor.count > 0
        }

    private fun pragmaUserVersion(database: androidx.sqlite.db.SupportSQLiteDatabase): Int =
        database.query("PRAGMA user_version").use { cursor ->
            check(cursor.moveToFirst()) { "PRAGMA user_version returned no rows" }
            cursor.getInt(0)
        }

    private fun querySingleString(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): String =
        database.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getString(0)
        }
}
