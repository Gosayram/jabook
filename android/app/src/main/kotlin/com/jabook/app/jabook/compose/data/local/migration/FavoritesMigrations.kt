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

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Room"

/**
 * Migration from database version 6 to 7.
 * Adds favorites table for managing favorite audiobooks.
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                Log.i(TAG, "🔄 Starting migration 6→7")
                val startTime = System.currentTimeMillis()
                // Create favorites table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorites (
                        topic_id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        category TEXT NOT NULL,
                        size TEXT NOT NULL,
                        seeders INTEGER NOT NULL DEFAULT 0,
                        leechers INTEGER NOT NULL DEFAULT 0,
                        magnet_url TEXT NOT NULL,
                        cover_url TEXT,
                        performer TEXT,
                        genres TEXT,
                        added_date TEXT NOT NULL,
                        added_to_favorites TEXT NOT NULL,
                        duration TEXT,
                        bitrate TEXT,
                        audio_codec TEXT
                    )
                    """.trimIndent(),
                )

                // Create index on added_to_favorites for efficient sorting
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_favorites_added_to_favorites " +
                        "ON favorites (added_to_favorites)",
                )
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "✅ Migration 6→7 completed successfully (${duration}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Migration 6→7 failed: ${e.message}", e)
                throw e
            }
        }
    }
