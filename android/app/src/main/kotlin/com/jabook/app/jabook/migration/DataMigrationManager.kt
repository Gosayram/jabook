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

package com.jabook.app.jabook.migration

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for data migration from Flutter to Kotlin.
 * TODO: Implement full migration logic.
 */
@Singleton
public class DataMigrationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val jabookDatabase: JabookDatabase,
        private val preferencesDataStore: DataStore<Preferences>,
        private val bookIdentifier: com.jabook.app.jabook.compose.data.local.scanner.BookIdentifier,
    ) {
        public companion object {
            private const val TAG = "DataMigrationManager"
            private const val PREFS_NAME = "FlutterSharedPreferences"
            private const val KEY_PLAYER_STATE = "flutter.player_state"
            private const val KEY_MIGRATION_COMPLETED = "migration_completed_v1"
        }

        suspend fun needsMigration(): Boolean =
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                // Check if already migrated
                if (prefs.getBoolean(KEY_MIGRATION_COMPLETED, false)) {
                    Log.d(TAG, "Migration already completed")
                    return@withContext false
                }

                // Check if legacy state exists
                val hasLegacyState = prefs.contains(KEY_PLAYER_STATE)
                Log.d(TAG, "needsMigration: $hasLegacyState")
                hasLegacyState
            }

        suspend fun migrateFromFlutter(): MigrationResult =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Starting migration from Flutter...")
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString =
                        prefs.getString(KEY_PLAYER_STATE, null)
                            ?: return@withContext MigrationResult.Failure(Exception("No player state found"))

                    val json = org.json.JSONObject(jsonString)
                    val groupPath = json.getString("groupPath")
                    val currentPosition = json.optLong("currentPosition", 0L)
                    val currentIndex = json.optInt("currentIndex", 0)

                    // Metadata
                    val metadataJson = json.optJSONObject("metadata")
                    val album = metadataJson?.optString("album")
                    val artist = metadataJson?.optString("artist") ?: metadataJson?.optString("albumArtist")
                    val title = metadataJson?.optString("title") ?: java.io.File(groupPath).name

                    // Generate ID consistent with Scanner
                    val bookId =
                        bookIdentifier.generateBookId(
                            directory = groupPath,
                            album = album,
                            artist = artist,
                        )

                    Log.i(TAG, "Migrating book: $title (ID: $bookId) from $groupPath")

                    // Create BookEntity
                    // We treat it as "Partial" because we haven't scanned it fully yet
                    // But we populate enough for playback resumption
                    val bookEntity =
                        com.jabook.app.jabook.compose.data.local.entity.BookEntity(
                            id = bookId,
                            title = title,
                            author = artist ?: "Unknown",
                            coverUrl = metadataJson?.optString("coverPath"), // Local path usually
                            description = null,
                            totalDuration = 0L, // Unknown until scan
                            currentPosition = currentPosition,
                            currentChapterIndex = currentIndex,
                            localPath = groupPath,
                            addedDate = System.currentTimeMillis(),
                            lastPlayedDate = System.currentTimeMillis(),
                            isFavorite = false,
                        )

                    // Insert into DB
                    jabookDatabase.booksDao().insertBook(bookEntity)

                    // Mark as migrated
                    prefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()

                    Log.i(TAG, "Migration successful for 1 book")
                    MigrationResult.Success(booksCount = 1, chaptersCount = 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Migration failed", e)
                    MigrationResult.Failure(e)
                }
            }
    }

/**
 * Result of migration operation.
 */
public sealed class MigrationResult {
    public data class Success(
        public val booksCount: Int,
        public val chaptersCount: Int,
    ) : MigrationResult()

    public data class Failure(
        public val error: Exception,
    ) : MigrationResult()
}
