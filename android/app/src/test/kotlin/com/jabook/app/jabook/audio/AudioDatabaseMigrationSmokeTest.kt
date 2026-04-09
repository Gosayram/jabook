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

package com.jabook.app.jabook.audio

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.audio.data.local.database.AudioDatabase
import com.jabook.app.jabook.audio.data.local.database.migration.AudioDatabaseMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioDatabaseMigrationSmokeTest {
    private lateinit var context: Context
    private var database: AudioDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AUDIO_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(AUDIO_DATABASE_NAME)
    }

    @Test
    fun `audio database initializes at version 3 with listening sessions schema`() {
        database = AudioDataModule.provideAudioDatabase(context)
        val sqlDb = requireNotNull(database).openHelper.writableDatabase

        assertEquals(3, pragmaUserVersion(sqlDb))
        assertTrue(tableExists(sqlDb, "playback_positions"))
        assertTrue(tableExists(sqlDb, "playlists"))
        assertTrue(tableExists(sqlDb, "chapter_metadata"))
        assertTrue(tableExists(sqlDb, "saved_player_states"))
        assertTrue(tableExists(sqlDb, "listening_sessions"))
        assertTrue(indexExists(sqlDb, "index_listening_sessions_book_id"))
        assertTrue(indexExists(sqlDb, "index_listening_sessions_started_at"))
        assertTrue(indexExists(sqlDb, "index_listening_sessions_ended_at"))
    }

    @Test
    fun `migration contract includes 2 to 3 and creates listening sessions indexes`() {
        assertEquals(
            2,
            AudioDatabaseMigrations.MIGRATION_2_3.startVersion,
        )
        assertEquals(
            3,
            AudioDatabaseMigrations.MIGRATION_2_3.endVersion,
        )

        database = AudioDataModule.provideAudioDatabase(context)
        val currentSqlDb = requireNotNull(database).openHelper.writableDatabase
        currentSqlDb.execSQL("PRAGMA user_version = 2")
        requireNotNull(database).close()
        database = null

        database = AudioDataModule.provideAudioDatabase(context)
        val migratedSqlDb = requireNotNull(database).openHelper.writableDatabase

        assertEquals(3, pragmaUserVersion(migratedSqlDb))
        assertTrue(tableExists(migratedSqlDb, "listening_sessions"))
        assertTrue(indexExists(migratedSqlDb, "index_listening_sessions_book_id"))
        assertTrue(indexExists(migratedSqlDb, "index_listening_sessions_started_at"))
        assertTrue(indexExists(migratedSqlDb, "index_listening_sessions_ended_at"))
    }

    private fun tableExists(
        database: SupportSQLiteDatabase,
        tableName: String,
    ): Boolean =
        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'").use { cursor ->
            cursor.count > 0
        }

    private fun indexExists(
        database: SupportSQLiteDatabase,
        indexName: String,
    ): Boolean =
        database.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$indexName'").use { cursor ->
            cursor.count > 0
        }

    private fun pragmaUserVersion(database: SupportSQLiteDatabase): Int =
        database.query("PRAGMA user_version").use { cursor ->
            check(cursor.moveToFirst()) { "PRAGMA user_version returned no rows" }
            cursor.getInt(0)
        }

    private companion object {
        private const val AUDIO_DATABASE_NAME = "audio_database"
    }
}
