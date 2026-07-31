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
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_17_18
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_20_21
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_21_22
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_22_23
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_26_27
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_28_29
import com.jabook.app.jabook.compose.data.local.migration.createTopicsFts5Index
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
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
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

    private fun createCachedTopicsTable() {
        db.execSQL(
            """
            CREATE TABLE cached_topics (
                topic_id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                category TEXT NOT NULL,
                size TEXT NOT NULL,
                seeders INTEGER NOT NULL,
                leechers INTEGER NOT NULL,
                magnet_url TEXT,
                torrent_url TEXT,
                cover_url TEXT,
                timestamp INTEGER NOT NULL,
                last_updated INTEGER NOT NULL,
                index_version INTEGER NOT NULL
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

    private fun hasIndex(name: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='$name'")
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
    fun `migration 17 to 18 creates last played index for recent book queries`() {
        createBooksTable()
        db.execSQL(
            """
            CREATE TABLE chapters (
                id TEXT PRIMARY KEY NOT NULL,
                book_id TEXT NOT NULL,
                chapter_index INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        assertFalse(hasIndex("index_books_last_played_date"))

        MIGRATION_17_18.migrate(db)

        assertTrue(hasIndex("index_books_last_played_date"))
    }

    @Test
    fun `migration 17 to 18 version contract is correct`() {
        assertEquals(17, MIGRATION_17_18.startVersion)
        assertEquals(18, MIGRATION_17_18.endVersion)
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

    @Test
    fun `migration 22 to 23 creates and keeps topics FTS index synchronized`() {
        assumeTrue("FTS5 not available in this SQLite build", isFts5Available())
        createCachedTopicsTable()
        db.execSQL(
            "INSERT INTO cached_topics VALUES ('t1', 'Ёжик в тумане', 'Автор', 'Аудиокниги', '1 GB', 5, 0, NULL, NULL, NULL, 1, 1, 1)",
        )

        MIGRATION_22_23.migrate(db)

        assertTrue(hasTable("topics_fts"))
        assertTrue(hasTrigger("topics_fts_ai"))
        assertTrue(hasTrigger("topics_fts_ad"))
        assertTrue(hasTrigger("topics_fts_au"))
        db.query("SELECT COUNT(*) FROM topics_fts WHERE topics_fts MATCH 'ежик'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        db.execSQL("UPDATE cached_topics SET title = 'Лиса' WHERE topic_id = 't1'")
        db.query("SELECT COUNT(*) FROM topics_fts WHERE topics_fts MATCH 'лиса'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun `migration 22 to 23 version contract is correct`() {
        assertEquals(22, MIGRATION_22_23.startVersion)
        assertEquals(23, MIGRATION_22_23.endVersion)
    }

    @Test
    fun `topics FTS setup is safe to run when the index already exists`() {
        assumeTrue("FTS5 not available in this SQLite build", isFts5Available())
        createCachedTopicsTable()
        db.execSQL(
            "INSERT INTO cached_topics VALUES ('t1', 'Книга', 'Автор', 'Аудиокниги', '1 GB', 5, 0, NULL, NULL, NULL, 1, 1, 1)",
        )

        createTopicsFts5Index(db)
        createTopicsFts5Index(db)

        db.query("SELECT COUNT(*) FROM topics_fts WHERE topics_fts MATCH 'книга'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun `migration 26 to 27 adds eq_preset_override column to books`() {
        createBooksTable()
        assertFalse(hasColumn("books", "eq_preset_override"))
        db.execSQL(
            "INSERT INTO books (id, title, author, description, added_date) VALUES ('b1', 'Test Book', 'Author', 'Desc', 1000)",
        )

        MIGRATION_26_27.migrate(db)

        assertTrue(hasColumn("books", "eq_preset_override"))
        val cursor =
            db.query("SELECT eq_preset_override FROM books WHERE id = 'b1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            val idx = it.getColumnIndex("eq_preset_override")
            assertTrue(it.isNull(idx))
        }
    }

    @Test
    fun `migration 26 to 27 is idempotent`() {
        createBooksTable()

        MIGRATION_26_27.migrate(db)
        MIGRATION_26_27.migrate(db)

        assertTrue(hasColumn("books", "eq_preset_override"))
    }

    @Test
    fun `migration 26 to 27 version contract is correct`() {
        assertEquals(26, MIGRATION_26_27.startVersion)
        assertEquals(27, MIGRATION_26_27.endVersion)
    }

    @Test
    fun `migration 26 to 27 preserves existing book data`() {
        createBooksTable()
        db.execSQL(
            "INSERT INTO books (id, title, author, description, added_date) VALUES ('b1', 'Existing Book', 'Existing Author', 'A description', 1000)",
        )

        MIGRATION_26_27.migrate(db)

        val cursor = db.query("SELECT id, title, author FROM books WHERE id = 'b1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("b1", it.getString(0))
            assertEquals("Existing Book", it.getString(1))
            assertEquals("Existing Author", it.getString(2))
        }
    }

    @Test
    fun `migration 28 to 29 preserves bookmarks and marks legacy normalized position as unknown`() {
        createBooksTable()
        createBookmarksTable()
        db.execSQL(
            """
            INSERT INTO books (id, title, author, description, added_date)
            VALUES ('b1', 'Existing Book', 'Author', 'Description', 1000)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO bookmarks (id, book_id, chapter_index, position_ms, note_text, created_at, updated_at)
            VALUES ('bookmark-1', 'b1', 3, 12500, 'Keep this note', 1000, 2000)
            """.trimIndent(),
        )

        MIGRATION_28_29.migrate(db)

        assertTrue(hasColumn("bookmarks", "normalized_position"))
        db
            .query(
                """
                SELECT chapter_index, position_ms, normalized_position, note_text
                FROM bookmarks WHERE id = 'bookmark-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals(12500L, cursor.getLong(1))
                assertEquals(0f, cursor.getFloat(2))
                assertEquals("Keep this note", cursor.getString(3))
            }
    }

    @Test
    fun `migration 28 to 29 is idempotent`() {
        createBooksTable()
        createBookmarksTable()

        MIGRATION_28_29.migrate(db)
        MIGRATION_28_29.migrate(db)

        assertTrue(hasColumn("bookmarks", "normalized_position"))
    }

    @Test
    fun `migration 28 to 29 version contract is correct`() {
        assertEquals(28, MIGRATION_28_29.startVersion)
        assertEquals(29, MIGRATION_28_29.endVersion)
    }
}
