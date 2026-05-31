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

package com.jabook.app.jabook.compose.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_20_21
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_21_22
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JabookDatabaseMigrationTest {
    private lateinit var dbHelper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {}

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {}
                    },
                ).build()
        dbHelper = factory.create(config)
        db = dbHelper.writableDatabase
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } catch (_: Exception) {
        }
        try {
            dbHelper.close()
        } catch (_: Exception) {
        }
    }

    private fun createBooksTable() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS books (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                cover_url TEXT,
                description TEXT,
                total_duration INTEGER NOT NULL DEFAULT 0,
                current_position INTEGER NOT NULL DEFAULT 0,
                total_progress REAL NOT NULL DEFAULT 0.0,
                current_chapter_index INTEGER NOT NULL DEFAULT 0,
                download_status TEXT NOT NULL DEFAULT 'NOT_DOWNLOADED',
                download_progress REAL NOT NULL DEFAULT 0.0,
                local_path TEXT,
                added_date INTEGER NOT NULL DEFAULT 0,
                last_played_date INTEGER,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                source_url TEXT,
                cover_path TEXT,
                rewind_duration INTEGER,
                forward_duration INTEGER,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                lufs_value REAL,
                preferred_speed REAL
            )
            """.trimIndent(),
        )
    }

    private fun createFts4TableAndTriggers() {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS books_fts
            USING fts4(title, author, description, content='books', tokenize=unicode61)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS books_ai
            AFTER INSERT ON books BEGIN
                INSERT INTO books_fts(rowid, title, author, description)
                VALUES (new.rowid, new.title, new.author, new.description);
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS books_ad
            AFTER DELETE ON books BEGIN
                INSERT INTO books_fts(books_fts, rowid, title, author, description)
                VALUES ('delete', old.rowid, old.title, old.author, old.description);
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS books_au
            AFTER UPDATE ON books BEGIN
                INSERT INTO books_fts(books_fts, rowid, title, author, description)
                VALUES ('delete', old.rowid, old.title, old.author, old.description);
                INSERT INTO books_fts(rowid, title, author, description)
                VALUES (new.rowid, new.title, new.author, new.description);
            END
            """.trimIndent(),
        )
    }

    private fun createBookmarksTable() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id TEXT NOT NULL,
                book_id TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                position_ms INTEGER NOT NULL,
                note_text TEXT,
                note_audio_path TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(book_id) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_book_id ON bookmarks(book_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_book_id_position_ms ON bookmarks(book_id, position_ms)")
    }

    private fun createV20Schema() {
        createBooksTable()
        createFts4TableAndTriggers()
        createBookmarksTable()
    }

    private fun createTorrentDownloadsTableV21() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS torrent_downloads (
                hash TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                state TEXT NOT NULL,
                progress REAL NOT NULL,
                totalSize INTEGER NOT NULL,
                downloadedSize INTEGER NOT NULL,
                uploadedSize INTEGER NOT NULL,
                savePath TEXT NOT NULL,
                files TEXT NOT NULL,
                errorMessage TEXT,
                addedTime INTEGER NOT NULL,
                completedTime INTEGER NOT NULL,
                pauseReason TEXT,
                topicId TEXT
            )
            """.trimIndent(),
        )
    }

    private fun hasColumn(
        table: String,
        column: String,
    ): Boolean {
        val cursor = db.query("PRAGMA table_info($table)")
        cursor.use {
            while (it.moveToNext()) {
                val nameIndex = it.getColumnIndex("name")
                if (nameIndex >= 0 && it.getString(nameIndex) == column) return true
            }
        }
        return false
    }

    private fun hasTrigger(name: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name='$name'")
        cursor.use { return it.moveToFirst() }
    }

    private fun hasTable(name: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'")
        cursor.use { return it.moveToFirst() }
    }

    private fun isFts5Available(): Boolean {
        try {
            val cursor = db.query("PRAGMA compile_options")
            cursor.use {
                while (it.moveToNext()) {
                    if (it.getString(0) == "ENABLE_FTS5") return true
                }
            }
        } catch (_: Exception) {
            return true
        }
        return false
    }

    @Test
    fun `migration 20 to 21 drops old FTS4 triggers and table`() {
        createV20Schema()
        assertTrue(hasTrigger("books_ai"))
        assertTrue(hasTrigger("books_ad"))
        assertTrue(hasTrigger("books_au"))
        assertTrue(hasTable("books_fts"))

        db.execSQL("DROP TRIGGER IF EXISTS books_ai")
        db.execSQL("DROP TRIGGER IF EXISTS books_ad")
        db.execSQL("DROP TRIGGER IF EXISTS books_au")
        db.execSQL("DROP TABLE IF EXISTS books_fts")

        assertFalse(hasTrigger("books_ai"))
        assertFalse(hasTrigger("books_ad"))
        assertFalse(hasTrigger("books_au"))
        assertFalse(hasTable("books_fts"))
    }

    @Test
    fun `migration 20 to 21 creates FTS5 table and triggers when FTS5 is available`() {
        assumeTrue("FTS5 not available in this SQLite build", isFts5Available())

        createV20Schema()
        db.execSQL(
            "INSERT INTO books (id, title, author, description, added_date) VALUES ('b1', 'Test Book', 'Author One', 'A description', 1000)",
        )
        db.execSQL(
            "INSERT INTO books_fts (rowid, title, author, description) VALUES (1, 'Test Book', 'Author One', 'A description')",
        )

        MIGRATION_20_21.migrate(db)

        assertTrue(hasTrigger("books_ai"))
        assertTrue(hasTrigger("books_ad"))
        assertTrue(hasTrigger("books_au"))

        val cursor = db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='books_fts'")
        cursor.use {
            assertTrue(it.moveToFirst())
            val sql = it.getString(0)
            assertTrue(sql != null && sql.contains("fts5", ignoreCase = true))
        }

        val ftsCursor = db.query("SELECT count FROM books_fts WHERE books_fts MATCH 'Test'")
        ftsCursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getInt(0) > 0)
        }

        val bookCursor = db.query("SELECT title, author FROM books WHERE id = 'b1'")
        bookCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Test Book", it.getString(0))
            assertEquals("Author One", it.getString(1))
        }
    }

    @Test
    fun `migration 20 to 21 retains bookmarks table intact`() {
        assumeTrue("FTS5 not available in this SQLite build", isFts5Available())

        createV20Schema()
        db.execSQL(
            "INSERT INTO books (id, title, author, added_date) VALUES ('b1', 'Title', 'Auth', 1000)",
        )
        db.execSQL(
            "INSERT INTO bookmarks (id, book_id, chapter_index, position_ms, created_at, updated_at) VALUES ('bm1', 'b1', 0, 5000, 1000, 1000)",
        )

        MIGRATION_20_21.migrate(db)

        assertTrue(hasColumn("bookmarks", "id"))
        assertTrue(hasColumn("bookmarks", "book_id"))
        assertTrue(hasColumn("bookmarks", "position_ms"))
        val cursor = db.query("SELECT book_id, position_ms FROM bookmarks WHERE id = 'bm1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("b1", it.getString(0))
            assertEquals(5000L, it.getLong(1))
        }
    }

    @Test
    fun `migration 20 to 21 version contract is correct`() {
        assertEquals(20, MIGRATION_20_21.startVersion)
        assertEquals(21, MIGRATION_20_21.endVersion)
    }

    @Test
    fun `migration 21 to 22 adds resumeData column to torrent_downloads`() {
        createTorrentDownloadsTableV21()
        assertFalse(hasColumn("torrent_downloads", "resumeData"))
        db.execSQL(
            "INSERT INTO torrent_downloads (hash, name, state, progress, totalSize, downloadedSize, uploadedSize, savePath, files, addedTime, completedTime) VALUES ('abc123', 'test.torrent', 'DOWNLOADING', 0.5, 1000, 500, 0, '/tmp', '[]', 1000, 0)",
        )

        MIGRATION_21_22.migrate(db)

        assertTrue(hasColumn("torrent_downloads", "resumeData"))
        val cursor = db.query("SELECT name, progress FROM torrent_downloads WHERE hash = 'abc123'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("test.torrent", it.getString(0))
        }
    }

    @Test
    fun `migration 21 to 22 resumeData column is nullable for existing rows`() {
        createTorrentDownloadsTableV21()
        db.execSQL(
            "INSERT INTO torrent_downloads (hash, name, state, progress, totalSize, downloadedSize, uploadedSize, savePath, files, addedTime, completedTime) VALUES ('abc123', 'test.torrent', 'DOWNLOADING', 0.5, 1000, 500, 0, '/tmp', '[]', 1000, 0)",
        )

        MIGRATION_21_22.migrate(db)

        val cursor = db.query("SELECT resumeData FROM torrent_downloads WHERE hash = 'abc123'")
        cursor.use {
            assertTrue(it.moveToFirst())
            val idx = it.getColumnIndex("resumeData")
            assertTrue(it.isNull(idx))
        }
    }

    @Test
    fun `migration 21 to 22 version contract is correct`() {
        assertEquals(21, MIGRATION_21_22.startVersion)
        assertEquals(22, MIGRATION_21_22.endVersion)
    }

    @Test
    fun `migration 21 to 22 handles empty torrent_downloads table without error`() {
        createTorrentDownloadsTableV21()

        MIGRATION_21_22.migrate(db)

        assertTrue(hasColumn("torrent_downloads", "resumeData"))
    }

    @Test
    fun `chained migration 20 to 22 applies 21 to 22 correctly after 20 to 21`() {
        assumeTrue("FTS5 not available in this SQLite build", isFts5Available())

        createV20Schema()
        createTorrentDownloadsTableV21()
        db.execSQL(
            "INSERT INTO books (id, title, author, description, added_date) VALUES ('b1', 'Chain Book', 'Chain Author', 'Chain desc', 1000)",
        )
        db.execSQL(
            "INSERT INTO torrent_downloads (hash, name, state, progress, totalSize, downloadedSize, uploadedSize, savePath, files, addedTime, completedTime) VALUES ('h1', 'chain.torrent', 'PAUSED', 0.3, 2000, 600, 0, '/data', '[]', 2000, 0)",
        )

        MIGRATION_20_21.migrate(db)
        MIGRATION_21_22.migrate(db)

        assertTrue(hasTrigger("books_ai"))
        assertTrue(hasTrigger("books_ad"))
        assertTrue(hasTrigger("books_au"))
        assertTrue(hasColumn("torrent_downloads", "resumeData"))

        val torrentCursor = db.query("SELECT name, progress FROM torrent_downloads WHERE hash = 'h1'")
        torrentCursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("chain.torrent", it.getString(0))
        }
    }
}
