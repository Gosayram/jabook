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

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jabook.app.jabook.compose.data.local.dao.BookmarkDao
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.dao.CookiesDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadQueueDao
import com.jabook.app.jabook.compose.data.local.dao.FavoriteDao
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.BookFtsEntity
import com.jabook.app.jabook.compose.data.local.entity.BookmarkEntity
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import com.jabook.app.jabook.compose.data.local.entity.CookieEntity
import com.jabook.app.jabook.compose.data.local.entity.DownloadHistoryEntity
import com.jabook.app.jabook.compose.data.local.entity.DownloadQueueEntity
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchQueryEntity
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadDao
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadEntity

/**
 * The Room database for this app.
 *
 * Database version 2: Added new fields to BookEntity and ChapterEntity for
 * enhanced library and playback features.
 * Database version 3: Added SearchHistoryEntity for search history persistence.
 * Database version 4: Added CookieEntity for multi-stage cookie persistence.
 * Database version 5: Added download_queue table for download queue management.
 * Database version 6: Added download_history table for tracking completed/failed downloads.
 * Database version 7: Added favorites table for favorite audiobooks management.
 * Database version 8: Fix some issues with database migration.
 * Database version 9: Added scan_paths table for custom scan directory configuration.
 * Database version 10: Added index on chapter_index for faster chapter sorting.
 * Database version 11: Added torrent_downloads table for torrent download persistence.
 * Database version 12: Added cached_topics and search_query_map tables for offline search.
 * Database version 13: Added last_updated and index_version fields to cached_topics, plus search indices.
 * Database version 14: Added search indices on title, author, timestamp, and seeders for faster queries.
 * Database version 15: Update cached_topics with fallback category for RuTracker search fix.
 * Database version 16: Added books_fts (FTS virtual index) for fast local library search.
 * Database version 17: Added last_scan_timestamp to scan_paths for incremental scanning.
 * Database version 18: Added hot-path indices for books/chapters query performance.
 * Database version 19: Added lufs_value and preferred_speed columns for per-book loudness analysis and speed preference.
 * Database version 20: Added bookmarks table for timeline bookmarks and voice-note metadata.
 */
@Database(
    entities = [
        BookEntity::class,
        BookFtsEntity::class,
        BookmarkEntity::class,
        ChapterEntity::class,
        SearchHistoryEntity::class,
        CookieEntity::class,
        DownloadQueueEntity::class,
        DownloadHistoryEntity::class,
        FavoriteEntity::class,
        ScanPathEntity::class,
        TorrentDownloadEntity::class,
        CachedTopicEntity::class,
        SearchQueryEntity::class,
    ],
    version = 20,
    exportSchema = true, // Enable schema export for migration validation and debugging
)
public abstract class JabookDatabase : RoomDatabase() {
    public abstract fun booksDao(): BooksDao

    public abstract fun bookmarkDao(): BookmarkDao

    public abstract fun chaptersDao(): ChaptersDao

    public abstract fun searchHistoryDao(): SearchHistoryDao

    public abstract fun cookiesDao(): CookiesDao

    public abstract fun downloadQueueDao(): DownloadQueueDao

    public abstract fun downloadHistoryDao(): DownloadHistoryDao

    public abstract fun favoriteDao(): FavoriteDao

    public abstract fun scanPathDao(): ScanPathDao

    public abstract fun torrentDownloadDao(): TorrentDownloadDao

    public abstract fun offlineSearchDao(): OfflineSearchDao
}
