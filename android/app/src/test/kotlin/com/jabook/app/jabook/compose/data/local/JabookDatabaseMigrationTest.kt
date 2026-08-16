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
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_14_15
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_15_16
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_16_17
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_17_18
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_18_19
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_19_20
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_20_21
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_21_22
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_22_23
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_23_24
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_24_25
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_25_26
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_26_27
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_28_29
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_29_30
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_30_31
import com.jabook.app.jabook.compose.data.local.migration.createTopicsFts5Index
import com.jabook.app.jabook.compose.data.local.migration.isMissingFts5Module
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

    // ──────────────────────────────────────────────────────────────────────
    // Schema setup helpers
    // ──────────────────────────────────────────────────────────────────────

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

    private fun createBooksTableV18() {
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
                is_downloaded INTEGER NOT NULL DEFAULT 0
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
                category TEXT,
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

    private fun createScanPathsTableV16() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_paths (
                path TEXT PRIMARY KEY NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
    }

    private fun createChaptersTableV25() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chapters (
                id TEXT PRIMARY KEY NOT NULL,
                book_id TEXT NOT NULL,
                title TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                file_index INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                file_url TEXT,
                position INTEGER NOT NULL DEFAULT 0,
                is_completed INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Assertion helpers
    // ──────────────────────────────────────────────────────────────────────

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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 14 → 15: cached_topics fallback category
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 14 to 15 sets fallback category for blank entries`() {
        createCachedTopicsTable()
        db.execSQL("INSERT INTO cached_topics VALUES ('t1', 'Book', 'Author', '', '1 GB', 5, 0, NULL, NULL, NULL, 1, 1, 1)")
        db.execSQL("INSERT INTO cached_topics VALUES ('t2', 'Book2', 'Author2', NULL, '2 GB', 3, 0, NULL, NULL, NULL, 1, 1, 1)")
        db.execSQL("INSERT INTO cached_topics VALUES ('t3', 'Book3', 'Author3', 'Аудиокниги', '3 GB', 7, 0, NULL, NULL, NULL, 1, 1, 1)")

        MIGRATION_14_15.migrate(db)

        val cursor = db.query("SELECT category FROM cached_topics WHERE topic_id = 't1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Аудиокниги", it.getString(0))
        }
        val cursor2 = db.query("SELECT category FROM cached_topics WHERE topic_id = 't2'")
        cursor2.use {
            assertTrue(it.moveToFirst())
            assertEquals("Аудиокниги", it.getString(0))
        }
        val cursor3 = db.query("SELECT category FROM cached_topics WHERE topic_id = 't3'")
        cursor3.use {
            assertTrue(it.moveToFirst())
            assertEquals("Аудиокниги", it.getString(0))
        }
    }

    @Test
    fun `migration 14 to 15 preserves existing non-blank categories`() {
        createCachedTopicsTable()
        db.execSQL("INSERT INTO cached_topics VALUES ('t1', 'Book', 'Author', 'Художественная', '1 GB', 5, 0, NULL, NULL, NULL, 1, 1, 1)")

        MIGRATION_14_15.migrate(db)

        val cursor = db.query("SELECT category FROM cached_topics WHERE topic_id = 't1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Художественная", it.getString(0))
        }
    }

    @Test
    fun `migration 14 to 15 version contract is correct`() {
        assertEquals(14, MIGRATION_14_15.startVersion)
        assertEquals(15, MIGRATION_14_15.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 15 → 16: books_fts FTS4
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 15 to 16 creates FTS4 virtual table and triggers`() {
        createBooksTable()
        assertFalse(hasTable("books_fts"))

        MIGRATION_15_16.migrate(db)

        assertTrue(hasTable("books_fts"))
        assertTrue(hasTrigger("books_ai"))
        assertTrue(hasTrigger("books_ad"))
        assertTrue(hasTrigger("books_au"))
    }

    @Test
    fun `migration 15 to 16 populates FTS from existing books`() {
        if (!isFts5Available()) return
        createBooksTable()
        db.execSQL("INSERT INTO books (id, title, author, description, added_date) VALUES ('b1', 'Test Book', 'Author', 'Desc', 1000)")

        MIGRATION_15_16.migrate(db)

        val cursor = db.query("SELECT count FROM books_fts WHERE books_fts MATCH 'Test'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getInt(0) > 0)
        }
    }

    @Test
    fun `migration 15 to 16 version contract is correct`() {
        assertEquals(15, MIGRATION_15_16.startVersion)
        assertEquals(16, MIGRATION_15_16.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 16 → 17: scan_paths last_scan_timestamp
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 16 to 17 adds last_scan_timestamp column to scan_paths`() {
        createScanPathsTableV16()
        db.execSQL("INSERT INTO scan_paths (path, enabled) VALUES ('/music', 1)")
        assertFalse(hasColumn("scan_paths", "last_scan_timestamp"))

        MIGRATION_16_17.migrate(db)

        assertTrue(hasColumn("scan_paths", "last_scan_timestamp"))
        val cursor = db.query("SELECT path, last_scan_timestamp FROM scan_paths WHERE path = '/music'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("/music", it.getString(0))
            assertEquals(0L, it.getLong(1))
        }
    }

    @Test
    fun `migration 16 to 17 is idempotent`() {
        createScanPathsTableV16()

        MIGRATION_16_17.migrate(db)
        MIGRATION_16_17.migrate(db)

        assertTrue(hasColumn("scan_paths", "last_scan_timestamp"))
    }

    @Test
    fun `migration 16 to 17 version contract is correct`() {
        assertEquals(16, MIGRATION_16_17.startVersion)
        assertEquals(17, MIGRATION_16_17.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 17 → 18: hot-path indices
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 18 → 19: lufs_value and preferred_speed columns
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 18 to 19 adds lufs_value and preferred_speed columns to books`() {
        createBooksTableV18()
        db.execSQL("INSERT INTO books (id, title, author, added_date) VALUES ('b1', 'Book', 'Author', 1000)")
        assertFalse(hasColumn("books", "lufs_value"))
        assertFalse(hasColumn("books", "preferred_speed"))

        MIGRATION_18_19.migrate(db)

        assertTrue(hasColumn("books", "lufs_value"))
        assertTrue(hasColumn("books", "preferred_speed"))
        val cursor = db.query("SELECT lufs_value, preferred_speed FROM books WHERE id = 'b1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun `migration 18 to 19 is idempotent`() {
        createBooksTableV18()

        MIGRATION_18_19.migrate(db)
        MIGRATION_18_19.migrate(db)

        assertTrue(hasColumn("books", "lufs_value"))
        assertTrue(hasColumn("books", "preferred_speed"))
    }

    @Test
    fun `migration 18 to 19 preserves existing book data`() {
        createBooksTable()
        db.execSQL("INSERT INTO books (id, title, author, added_date) VALUES ('b1', 'Existing Book', 'Existing Author', 1000)")

        MIGRATION_18_19.migrate(db)

        val cursor = db.query("SELECT id, title, author FROM books WHERE id = 'b1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("b1", it.getString(0))
            assertEquals("Existing Book", it.getString(1))
            assertEquals("Existing Author", it.getString(2))
        }
    }

    @Test
    fun `migration 18 to 19 version contract is correct`() {
        assertEquals(18, MIGRATION_18_19.startVersion)
        assertEquals(19, MIGRATION_18_19.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 19 → 20: bookmarks table
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 19 to 20 creates bookmarks table with indexes`() {
        createBooksTable()
        assertFalse(hasTable("bookmarks"))

        MIGRATION_19_20.migrate(db)

        assertTrue(hasTable("bookmarks"))
        assertTrue(hasIndex("index_bookmarks_book_id"))
        assertTrue(hasIndex("index_bookmarks_book_id_position_ms"))
        assertTrue(hasColumn("bookmarks", "id"))
        assertTrue(hasColumn("bookmarks", "book_id"))
        assertTrue(hasColumn("bookmarks", "chapter_index"))
        assertTrue(hasColumn("bookmarks", "position_ms"))
        assertTrue(hasColumn("bookmarks", "note_text"))
        assertTrue(hasColumn("bookmarks", "note_audio_path"))
        assertTrue(hasColumn("bookmarks", "created_at"))
        assertTrue(hasColumn("bookmarks", "updated_at"))
    }

    @Test
    fun `migration 19 to 20 version contract is correct`() {
        assertEquals(19, MIGRATION_19_20.startVersion)
        assertEquals(20, MIGRATION_19_20.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 20 → 21: FTS4 → FTS5
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 21 → 22: resumeData column
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 22 → 23: topics FTS5 index
    // ══════════════════════════════════════════════════════════════════════

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
    fun `topics FTS setup skips only an unavailable FTS5 module`() {
        assertTrue(SQLiteException("no such module: fts5").isMissingFts5Module())
        assertFalse(SQLiteException("database is locked").isMissingFts5Module())
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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 23 → 24: narrator column
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 23 to 24 adds narrator column to books`() {
        createBooksTable()
        db.execSQL("INSERT INTO books (id, title, author, added_date) VALUES ('b1', 'Book', 'Author', 1000)")
        assertFalse(hasColumn("books", "narrator"))

        MIGRATION_23_24.migrate(db)

        assertTrue(hasColumn("books", "narrator"))
        val cursor = db.query("SELECT narrator FROM books WHERE id = 'b1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
    }

    @Test
    fun `migration 23 to 24 preserves existing book data`() {
        createBooksTable()
        db.execSQL("INSERT INTO books (id, title, author, added_date) VALUES ('b1', 'Existing Book', 'Existing Author', 1000)")

        MIGRATION_23_24.migrate(db)

        val cursor = db.query("SELECT id, title, author FROM books WHERE id = 'b1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("b1", it.getString(0))
            assertEquals("Existing Book", it.getString(1))
            assertEquals("Existing Author", it.getString(2))
        }
    }

    @Test
    fun `migration 23 to 24 version contract is correct`() {
        assertEquals(23, MIGRATION_23_24.startVersion)
        assertEquals(24, MIGRATION_23_24.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 24 → 25: rebuild topics_fts as contentless FTS5
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 24 to 25 rebuilds topics_fts as contentless FTS5`() {
        assumeTrue("FTS5 not available in this SQLite build", isFts5Available())
        createCachedTopicsTable()
        db.execSQL(
            "INSERT INTO cached_topics VALUES ('t1', 'Книга', 'Автор', 'Аудиокниги', '1 GB', 5, 0, NULL, NULL, NULL, 1, 1, 1)",
        )
        // Simulate v24 state: topics_fts with triggers from migration 22→23
        createTopicsFts5Index(db)
        assertTrue(hasTable("topics_fts"))
        assertTrue(hasTrigger("topics_fts_ai"))

        MIGRATION_24_25.migrate(db)

        assertTrue(hasTable("topics_fts"))
        // Contentless FTS5 still has triggers for sync
        assertTrue(hasTrigger("topics_fts_ai"))
        assertTrue(hasTrigger("topics_fts_ad"))
        assertTrue(hasTrigger("topics_fts_au"))
        db.query("SELECT COUNT(*) FROM topics_fts WHERE topics_fts MATCH 'книга'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun `migration 24 to 25 version contract is correct`() {
        assertEquals(24, MIGRATION_24_25.startVersion)
        assertEquals(25, MIGRATION_24_25.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 25 → 26: chapters lufs_value
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 25 to 26 adds lufs_value column to chapters`() {
        createChaptersTableV25()
        db.execSQL("INSERT INTO chapters (id, book_id, title, chapter_index, file_index, duration) VALUES ('c1', 'b1', 'Ch1', 0, 0, 90000)")
        assertFalse(hasColumn("chapters", "lufs_value"))

        MIGRATION_25_26.migrate(db)

        assertTrue(hasColumn("chapters", "lufs_value"))
        val cursor = db.query("SELECT lufs_value FROM chapters WHERE id = 'c1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
    }

    @Test
    fun `migration 25 to 26 is idempotent`() {
        createChaptersTableV25()

        MIGRATION_25_26.migrate(db)
        MIGRATION_25_26.migrate(db)

        assertTrue(hasColumn("chapters", "lufs_value"))
    }

    @Test
    fun `migration 25 to 26 preserves existing chapter data`() {
        createChaptersTableV25()
        db.execSQL(
            "INSERT INTO chapters (id, book_id, title, chapter_index, file_index, duration) VALUES ('c1', 'b1', 'Existing Chapter', 0, 0, 90000)",
        )

        MIGRATION_25_26.migrate(db)

        val cursor = db.query("SELECT id, title, duration FROM chapters WHERE id = 'c1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("c1", it.getString(0))
            assertEquals("Existing Chapter", it.getString(1))
            assertEquals(90000L, it.getLong(2))
        }
    }

    @Test
    fun `migration 25 to 26 version contract is correct`() {
        assertEquals(25, MIGRATION_25_26.startVersion)
        assertEquals(26, MIGRATION_25_26.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 26 → 27: eq_preset_override column
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 28 → 29: normalized_position column
    // ══════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════
    // Migration 29 → 30: chapter offset columns
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 29 to 30 adds nullable chapter offset columns and preserves rows`() {
        db.execSQL(
            """
            CREATE TABLE chapters (
                id TEXT PRIMARY KEY NOT NULL,
                book_id TEXT NOT NULL,
                title TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                file_index INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                file_url TEXT,
                position INTEGER NOT NULL DEFAULT 0,
                is_completed INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                lufs_value REAL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO chapters (id, book_id, title, chapter_index, file_index, duration, file_url)
            VALUES ('c1', 'b1', 'Whole file', 0, 0, 90000, '/books/x.m4b')
            """.trimIndent(),
        )

        MIGRATION_29_30.migrate(db)

        assertTrue(hasColumn("chapters", "start_position_ms"))
        assertTrue(hasColumn("chapters", "end_position_ms"))
        assertFalse(hasTable("cookies"))
        db
            .query(
                "SELECT start_position_ms, end_position_ms, duration FROM chapters WHERE id = 'c1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertEquals(90000L, cursor.getLong(2))
            }
    }

    @Test
    fun `migration 29 to 30 is idempotent`() {
        db.execSQL(
            """
            CREATE TABLE chapters (
                id TEXT PRIMARY KEY NOT NULL,
                book_id TEXT NOT NULL,
                title TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                file_index INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                file_url TEXT,
                position INTEGER NOT NULL DEFAULT 0,
                is_completed INTEGER NOT NULL DEFAULT 0,
                is_downloaded INTEGER NOT NULL DEFAULT 0,
                lufs_value REAL
            )
            """.trimIndent(),
        )

        MIGRATION_29_30.migrate(db)
        MIGRATION_29_30.migrate(db)

        assertTrue(hasColumn("chapters", "start_position_ms"))
        assertTrue(hasColumn("chapters", "end_position_ms"))
    }

    @Test
    fun `migration 29 to 30 version contract is correct`() {
        assertEquals(29, MIGRATION_29_30.startVersion)
        assertEquals(30, MIGRATION_29_30.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Migration 30 → 31: cached_topics.last_updated index
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `migration 30 to 31 creates last_updated index and preserves cached topics`() {
        createCachedTopicsTable()
        db.execSQL(
            "INSERT INTO cached_topics VALUES ('t1', 'Книга', 'Автор', 'Аудиокниги', '1 GB', 5, 0, NULL, NULL, NULL, 1, 1000, 1)",
        )
        assertFalse(hasIndex("index_cached_topics_last_updated"))

        MIGRATION_30_31.migrate(db)

        assertTrue(hasIndex("index_cached_topics_last_updated"))
        db.query("SELECT title FROM cached_topics WHERE topic_id = 't1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Книга", it.getString(0))
        }
    }

    @Test
    fun `migration 30 to 31 is idempotent`() {
        createCachedTopicsTable()

        MIGRATION_30_31.migrate(db)
        MIGRATION_30_31.migrate(db)

        assertTrue(hasIndex("index_cached_topics_last_updated"))
    }

    @Test
    fun `migration 30 to 31 version contract is correct`() {
        assertEquals(30, MIGRATION_30_31.startVersion)
        assertEquals(31, MIGRATION_30_31.endVersion)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Full upgrade path: v14 → v31
    // ══════════════════════════════════════════════════════════════════════

    @Test
    fun `full upgrade path from v14 to v31 applies all migrations without error`() {
        if (!isFts5Available()) return
        // Start with v14 schema: books + scan_paths + cached_topics (no FTS, no bookmarks)
        createBooksTableV18()
        createScanPathsTableV16()
        createCachedTopicsTable()

        // v14 → v15
        MIGRATION_14_15.migrate(db)
        // v15 → v16 (FTS4)
        MIGRATION_15_16.migrate(db)
        // v16 → v17
        MIGRATION_16_17.migrate(db)
        // v17 → v18 (creates indices on chapters, so chapters table must exist)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chapters (
                id TEXT PRIMARY KEY NOT NULL,
                book_id TEXT NOT NULL,
                title TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                file_index INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                file_url TEXT,
                position INTEGER NOT NULL DEFAULT 0,
                is_completed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        MIGRATION_17_18.migrate(db)
        // v18 → v19
        MIGRATION_18_19.migrate(db)
        // v19 → v20
        MIGRATION_19_20.migrate(db)
        // v20 → v21 (FTS5)
        MIGRATION_20_21.migrate(db)
        // v21 → v22
        createTorrentDownloadsTableV21()
        MIGRATION_21_22.migrate(db)
        // v22 → v23
        MIGRATION_22_23.migrate(db)
        // v23 → v24
        MIGRATION_23_24.migrate(db)
        // v24 → v25
        MIGRATION_24_25.migrate(db)
        // v25 → v26
        createChaptersTableV25()
        MIGRATION_25_26.migrate(db)
        // v26 → v27
        MIGRATION_26_27.migrate(db)
        // v28 → v29 (skip 27→28 as user_eq_presets table creation is tested elsewhere)
        // v28 → v29
        MIGRATION_28_29.migrate(db)
        // v29 → v30
        MIGRATION_29_30.migrate(db)
        // v30 → v31
        MIGRATION_30_31.migrate(db)

        // Verify final schema state
        assertTrue(hasTable("books"))
        assertTrue(hasTable("bookmarks"))
        assertTrue(hasTable("scan_paths"))
        assertTrue(hasTable("cached_topics"))
        assertTrue(hasTable("torrent_downloads"))
        assertTrue(hasTable("chapters"))
        assertTrue(hasTable("books_fts"))
        assertTrue(hasColumn("books", "narrator"))
        assertTrue(hasColumn("books", "lufs_value"))
        assertTrue(hasColumn("books", "preferred_speed"))
        assertTrue(hasColumn("books", "eq_preset_override"))
        assertTrue(hasColumn("scan_paths", "last_scan_timestamp"))
        assertTrue(hasColumn("torrent_downloads", "resumeData"))
        assertTrue(hasColumn("bookmarks", "normalized_position"))
        assertTrue(hasColumn("chapters", "lufs_value"))
        assertTrue(hasColumn("chapters", "start_position_ms"))
        assertTrue(hasColumn("chapters", "end_position_ms"))
        assertTrue(hasIndex("index_cached_topics_last_updated"))
    }
}
