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
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Database migration from version 1 to version 2.
     *
     * Adds new columns to books and chapters tables for enhanced functionality.
     */
    private val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

    /**
     * Database migration from version 2 to version 3.
     *
     * Adds search_history table.
     */
    private val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

    @Provides
    @Singleton
    fun provideJabookDatabase(
        @ApplicationContext context: Context,
    ): JabookDatabase =
        Room
            .databaseBuilder(
                context,
                JabookDatabase::class.java,
                "jabook-database",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideBooksDao(database: JabookDatabase): BooksDao = database.booksDao()

    @Provides
    fun provideChaptersDao(database: JabookDatabase): ChaptersDao = database.chaptersDao()

    @Provides
    fun provideSearchHistoryDao(database: JabookDatabase): com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao =
        database.searchHistoryDao()
}
