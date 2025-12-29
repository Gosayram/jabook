// Copyright 2025 Jabook Contributors
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
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadQueueDao
import com.jabook.app.jabook.compose.data.local.dao.FavoriteDao
import com.jabook.app.jabook.compose.data.local.migration.MIGRATION_6_7
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val TAG = "Room"

    /**
     * Helper function to wrap migration with logging.
     */
    private fun createLoggedMigration(
        startVersion: Int,
        endVersion: Int,
        migrationBlock: (SupportSQLiteDatabase) -> Unit,
    ): Migration =
        object : Migration(startVersion, endVersion) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    Log.i(TAG, "🔄 Starting migration $startVersion→$endVersion")
                    val startTime = System.currentTimeMillis()
                    migrationBlock(db)
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "✅ Migration $startVersion→$endVersion completed successfully (${duration}ms)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Migration $startVersion→$endVersion failed: ${e.message}", e)
                    throw e
                }
            }
        }

    /**
     * Database migration from version 1 to version 2.
     *
     * Adds new columns to books and chapters tables for enhanced functionality.
     */
    private val MIGRATION_1_2 =
        createLoggedMigration(1, 2) { db ->
            // Add new columns to books table
            db.execSQL("ALTER TABLE books ADD COLUMN total_progress REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE books ADD COLUMN download_status TEXT NOT NULL DEFAULT 'NOT_DOWNLOADED'")
            db.execSQL("ALTER TABLE books ADD COLUMN local_path TEXT")
            db.execSQL("ALTER TABLE books ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE books ADD COLUMN source_url TEXT")

            // Add new columns to chapters table
            db.execSQL("ALTER TABLE chapters ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chapters ADD COLUMN is_completed INTEGER NOT NULL DEFAULT 0")

            // Update download_status based on is_downloaded for backwards compatibility
            db.execSQL(
                """
                UPDATE books 
                SET download_status = CASE 
                    WHEN is_downloaded = 1 THEN 'DOWNLOADED' 
                    ELSE 'NOT_DOWNLOADED' 
                END
                """.trimIndent(),
            )
        }

    /**
     * Database migration from version 2 to version 3.
     *
     * Adds search_history table.
     */
    private val MIGRATION_2_3 =
        createLoggedMigration(2, 3) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `query` TEXT NOT NULL, 
                    `timestamp` INTEGER NOT NULL, 
                    `result_count` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

    /**
     * Database migration from version 4 to version 5.
     *
     * Adds download_queue table for persistent download queue management.
     */
    private val MIGRATION_4_5 =
        createLoggedMigration(4, 5) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_queue` (
                    `bookId` TEXT PRIMARY KEY NOT NULL,
                    `priority` INTEGER NOT NULL,
                    `queuePosition` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

    /**
     * Database migration from version 5 to version 6.
     *
     * Adds download_history table for tracking completed/failed downloads.
     */
    private val MIGRATION_5_6 =
        createLoggedMigration(5, 6) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `bookTitle` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL,
                    `totalBytes` INTEGER,
                    `errorMessage` TEXT
                )
                """.trimIndent(),
            )
        }

    /**
     * Database migration from version 7 to version 8.
     *
     * Fixes issues with database migration (no schema changes).
     */
    private val MIGRATION_7_8 =
        createLoggedMigration(7, 8) { db ->
            // No schema changes
        }

    /**
     * Database migration from version 8 to version 9.
     *
     * Adds scan_paths table for custom scan directory configuration.
     */
    private val MIGRATION_8_9 =
        createLoggedMigration(8, 9) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scan_paths` (
                    `path` TEXT NOT NULL, 
                    `added_date` INTEGER NOT NULL, 
                    PRIMARY KEY(`path`)
                )
                """.trimIndent(),
            )
        }

    /**
     * Database migration from version 9 to version 10.
     *
     * Adds index on chapter_index for faster chapter sorting.
     */
    private val MIGRATION_9_10 =
        createLoggedMigration(9, 10) { db ->
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chapters_chapter_index ON chapters(chapter_index)")
        }

    /**
     * Database migration from version 10 to version 11.
     *
     * Adds torrent_downloads table for torrent download persistence.
     */
    private val MIGRATION_10_11 =
        createLoggedMigration(10, 11) { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `torrent_downloads` (
                    `hash` TEXT PRIMARY KEY NOT NULL,
                    `name` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `progress` REAL NOT NULL,
                    `totalSize` INTEGER NOT NULL,
                    `downloadedSize` INTEGER NOT NULL,
                    `uploadedSize` INTEGER NOT NULL,
                    `savePath` TEXT NOT NULL,
                    `files` TEXT NOT NULL,
                    `errorMessage` TEXT,
                    `addedTime` INTEGER NOT NULL,
                    `completedTime` INTEGER NOT NULL,
                    `pauseReason` TEXT
                )
                """.trimIndent(),
            )
        }

    /**
     * Database migration from version 11 to version 12.
     *
     * Adds cached_topics and search_query_map tables for offline search persistence.
     */
    private val MIGRATION_11_12 =
        createLoggedMigration(11, 12) { db ->
            // Create cached_topics table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cached_topics` (
                    `topic_id` TEXT PRIMARY KEY NOT NULL,
                    `title` TEXT NOT NULL,
                    `author` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `size` TEXT NOT NULL,
                    `seeders` INTEGER NOT NULL,
                    `leechers` INTEGER NOT NULL,
                    `magnet_url` TEXT,
                    `torrent_url` TEXT NOT NULL,
                    `cover_url` TEXT,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create search_query_map table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_query_map` (
                    `query` TEXT NOT NULL,
                    `topic_id` TEXT NOT NULL,
                    `rank` INTEGER NOT NULL,
                    PRIMARY KEY(`query`, `topic_id`),
                    FOREIGN KEY(`topic_id`) REFERENCES `cached_topics`(`topic_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            // Create indices for search_query_map
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_query_map_topic_id` ON `search_query_map` (`topic_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_query_map_query` ON `search_query_map` (`query`)")
        }

    /**
     * Database migration from version 12 to version 13.
     *
     * Adds last_updated and index_version fields to cached_topics for incremental updates.
     */
    private val MIGRATION_12_13 =
        createLoggedMigration(12, 13) { db ->
            // Add new columns with default values
            db.execSQL("ALTER TABLE `cached_topics` ADD COLUMN `last_updated` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `cached_topics` ADD COLUMN `index_version` INTEGER NOT NULL DEFAULT 1")

            // Update existing records to set last_updated = timestamp
            db.execSQL("UPDATE `cached_topics` SET `last_updated` = `timestamp` WHERE `last_updated` = 0")
        }

    /**
     * Database migration from version 13 to version 14.
     *
     * Adds search indices for faster queries on title, author, timestamp, and seeders.
     */
    private val MIGRATION_13_14 =
        createLoggedMigration(13, 14) { db ->
            // Add indices for faster search and sorting
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_topics_title` ON `cached_topics` (`title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_topics_author` ON `cached_topics` (`author`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_topics_timestamp` ON `cached_topics` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_topics_seeders` ON `cached_topics` (`seeders`)")
        }

    @Provides
    @Singleton
    fun provideJabookDatabase(
        @ApplicationContext context: Context,
    ): JabookDatabase {
        val builder =
            Room
                .databaseBuilder(
                    context,
                    JabookDatabase::class.java,
                    "jabook-database",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                )
                // Use coroutine context for queries (better integration with coroutines)
                // This replaces the need for setQueryExecutor and provides better performance
                .setQueryCoroutineContext(Dispatchers.IO)
                // PreparedStatementCache is enabled by default (size 25) for better query performance
                // This caches prepared SQL statements to avoid recompilation overhead
                // JournalMode.AUTOMATIC is the default - Room chooses WAL on modern devices, TRUNCATE on low-RAM
                // WAL provides better concurrency, TRUNCATE is more memory-efficient
                .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC)
        // In-memory invalidation tracking is enabled by default (better performance)
        // Can be disabled with setInMemoryTrackingMode(false) if memory is a concern
        // requireMigration is true by default - ensures migrations are always provided for safety

        // Add callback for database lifecycle events (onCreate, onOpen, onDestructiveMigration)
        builder.addCallback(
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Log.i("Room", "JabookDatabase created")
                    // Enable foreign key constraints for referential integrity
                    db.execSQL("PRAGMA foreign_keys = ON")
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Enable foreign key constraints on each database open
                    db.execSQL("PRAGMA foreign_keys = ON")
                    // Optimize for better query performance
                    db.execSQL("PRAGMA optimize")
                }

                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    Log.e(
                        TAG,
                        "❌ CRITICAL: Destructive migration occurred - all data was lost! This should never happen in production.",
                    )
                }
            },
        )

        // Add query callback for logging in debug builds only
        // Query callbacks have a small performance cost, so only enable in debug
        try {
            val isDebug =
                Class
                    .forName("com.jabook.app.jabook.BuildConfig")
                    .getField("DEBUG")
                    .get(null) as? Boolean ?: false
            if (isDebug) {
                builder.setQueryCallback(
                    Dispatchers.Unconfined,
                    RoomDatabase.QueryCallback { sqlQuery: String, bindArgs: List<Any?> ->
                        Log.d(
                            "Room",
                            "Query: $sqlQuery | Args: ${bindArgs.joinToString(", ")}",
                        )
                    },
                )
            }
        } catch (e: Exception) {
            // BuildConfig not available, skip query callback
            Log.d("Room", "BuildConfig not available, skipping query callback", e)
        }

        return builder.build()
    }

    @Provides
    fun provideOfflineSearchDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao =
        database.offlineSearchDao()

    @Provides
    fun provideBooksDao(database: JabookDatabase): BooksDao = database.booksDao()

    @Provides
    fun provideChaptersDao(database: JabookDatabase): ChaptersDao = database.chaptersDao()

    @Provides
    fun provideSearchHistoryDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao =
        database.searchHistoryDao()

    @Provides
    fun provideDownloadQueueDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.local.dao.DownloadQueueDao =
        database.downloadQueueDao()

    @Provides
    fun provideDownloadHistoryDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao =
        database.downloadHistoryDao()

    @Provides
    fun provideFavoriteDao(database: JabookDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideScanPathDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.local.dao.ScanPathDao = database.scanPathDao()

    @Provides
    fun provideTorrentDownloadDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.torrent.TorrentDownloadDao =
        database.torrentDownloadDao()
}
