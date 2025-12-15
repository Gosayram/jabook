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

package com.jabook.app.jabook.compose.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.dao.CookiesDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadQueueDao
import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import com.jabook.app.jabook.compose.data.local.entity.CookieEntity
import com.jabook.app.jabook.compose.data.local.entity.DownloadQueueEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity

/**
 * The Room database for this app.
 *
 * Version 5: Added DownloadQueueEntity for persistent download queue management.
 * Version 4: Added CookieEntity for multi-stage cookie persistence.
 * Version 3: Added SearchHistoryEntity for search history persistence.
 * Version 2: Added new fields to BookEntity and ChapterEntity for
 * enhanced library and playback features.
 */
@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        SearchHistoryEntity::class,
        CookieEntity::class,
        DownloadQueueEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class JabookDatabase : RoomDatabase() {
    abstract fun booksDao(): BooksDao

    abstract fun chaptersDao(): ChaptersDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun cookiesDao(): CookiesDao

    abstract fun downloadQueueDao(): DownloadQueueDao
}
