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
 * Migration from database version 14 to 15.
 * Updates cached_topics to set fallback category for blank entries.
 *
 * This ensures existing indexed topics (~58,000) have valid categories
 * for improved search functionality.
 */
public val MIGRATION_14_15: Migration =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                Log.i(TAG, "🔄 Starting migration 14→15 (RuTracker category fallback)")
                val startTime = System.currentTimeMillis()

                // Update all cached topics with blank category to use fallback
                db.execSQL(
                    """
                    UPDATE cached_topics 
                    SET category = 'Аудиокниги' 
                    WHERE category IS NULL OR category = ''
                    """.trimIndent(),
                )

                // Log progress to verify migration success
                val cursor = db.query("SELECT COUNT(*) FROM cached_topics WHERE category = 'Аудиокниги'")
                val updatedCount = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()

                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "✅ Migration 14→15 completed: updated $updatedCount topics (${duration}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Migration 14→15 failed: ${e.message}", e)
                throw e
            }
        }
    }
