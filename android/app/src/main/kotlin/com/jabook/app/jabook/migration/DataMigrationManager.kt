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
import java.io.File
import java.security.MessageDigest
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
            private val ALLOWED_STORAGE_ROOTS: List<String> = listOf("/storage", "/sdcard", "/mnt/media_rw")
            private val BLOCKED_SYSTEM_ROOTS: List<String> = listOf("/proc", "/sys", "/dev", "/system", "/apex", "/vendor")
        }

        public suspend fun needsMigration(): Boolean =
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

        public suspend fun migrateFromFlutter(): MigrationResult =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Starting migration from Flutter...")
                var insertedBookId: String? = null
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString =
                        prefs.getString(KEY_PLAYER_STATE, null)
                            ?: return@withContext MigrationResult.Failure(Exception("No player state found"))

                    val json = org.json.JSONObject(jsonString)
                    val rawGroupPath = json.getString("groupPath")
                    val groupPath = validateAndNormalizeLegacyGroupPath(rawGroupPath)
                    val currentPosition = json.optLong("currentPosition", 0L)
                    val currentIndex = json.optInt("currentIndex", 0)
                    val preflightReport = runMigrationPreflight(groupPath)
                    if (!preflightReport.ready) {
                        return@withContext MigrationResult.Failure(
                            error =
                                IllegalStateException(
                                    "Migration preflight failed: ${preflightReport.blockingIssues.joinToString("; ")}",
                                ),
                            preflightReport = preflightReport,
                        )
                    }

                    // Metadata
                    val metadataJson = json.optJSONObject("metadata")
                    val album = metadataJson?.optNormalizedString("album")
                    val artist = metadataJson?.optNormalizedString("artist") ?: metadataJson?.optNormalizedString("albumArtist")
                    val title = metadataJson?.optNormalizedString("title") ?: File(groupPath).name

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
                    insertedBookId = bookId

                    // Mark as migrated
                    prefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()

                    Log.i(TAG, "Migration successful for 1 book")
                    MigrationResult.Success(
                        booksCount = 1,
                        chaptersCount = 0,
                        legacyStateChecksum = computeLegacyStateChecksum(jsonString),
                        preflightReport = preflightReport,
                    )
                } catch (e: Exception) {
                    val rollbackReport = rollbackAfterFailure(insertedBookId)
                    Log.e(TAG, "Migration failed", e)
                    MigrationResult.Failure(
                        error = e,
                        rollbackReport = rollbackReport,
                    )
                }
            }

        private fun runMigrationPreflight(groupPath: String): MigrationPreflightReport {
            val sourceDirectory = File(groupPath)
            val sourceExists = sourceDirectory.exists()
            val sourceReadable = sourceDirectory.canRead()

            val destinationDirectory = runCatching { context.filesDir }.getOrNull()
            val destinationPath = destinationDirectory?.canonicalPath ?: "unknown"
            val destinationWritable = destinationDirectory?.let { it.exists() && it.canWrite() } ?: false

            val warnings =
                buildList {
                    if (!sourceExists) {
                        add("Source path does not exist yet; migration will preserve path reference")
                    } else if (!sourceReadable) {
                        add("Source path is not readable")
                    }
                }
            val blockingIssues =
                buildList {
                    if (destinationDirectory != null && !destinationWritable) {
                        add("App storage directory is not writable")
                    }
                }

            return MigrationPreflightReport(
                sourcePath = groupPath,
                sourceExists = sourceExists,
                sourceReadable = sourceReadable,
                destinationPath = destinationPath,
                destinationWritable = destinationWritable,
                warnings = warnings,
                blockingIssues = blockingIssues,
            )
        }

        private suspend fun rollbackAfterFailure(insertedBookId: String?): MigrationRollbackReport {
            if (insertedBookId.isNullOrBlank()) {
                return MigrationRollbackReport(attempted = false, succeeded = false, steps = emptyList())
            }

            return try {
                jabookDatabase.booksDao().deleteById(insertedBookId)
                MigrationRollbackReport(
                    attempted = true,
                    succeeded = true,
                    steps = listOf("deleted_migrated_book:$insertedBookId"),
                )
            } catch (rollbackError: Exception) {
                Log.e(TAG, "Rollback failed for migrated book $insertedBookId", rollbackError)
                MigrationRollbackReport(
                    attempted = true,
                    succeeded = false,
                    steps = listOf("failed_to_delete_migrated_book:$insertedBookId"),
                )
            }
        }

        private fun computeLegacyStateChecksum(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(payload.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { byte -> "%02x".format(byte) }
        }

        private fun validateAndNormalizeLegacyGroupPath(rawPath: String): String {
            val trimmedPath = rawPath.trim()
            if (trimmedPath.isBlank()) {
                throw IllegalArgumentException("Legacy groupPath is empty")
            }

            // Reject traversal-like segments early before canonicalization.
            val containsTraversalSegment =
                trimmedPath
                    .replace('\\', '/')
                    .split('/')
                    .any { segment -> segment == ".." }
            if (containsTraversalSegment) {
                throw IllegalArgumentException("Legacy groupPath contains traversal segment")
            }

            val normalizedPath =
                try {
                    File(trimmedPath).canonicalPath
                } catch (e: Exception) {
                    throw IllegalArgumentException("Legacy groupPath cannot be normalized", e)
                }

            if (!File(normalizedPath).isAbsolute) {
                throw IllegalArgumentException("Legacy groupPath must be absolute")
            }

            val isBlockedRoot =
                BLOCKED_SYSTEM_ROOTS.any { root ->
                    normalizedPath == root || normalizedPath.startsWith("$root/")
                }
            if (isBlockedRoot) {
                throw IllegalArgumentException("Legacy groupPath points to restricted system location")
            }

            val isAllowedRoot =
                ALLOWED_STORAGE_ROOTS.any { root ->
                    normalizedPath == root || normalizedPath.startsWith("$root/")
                }
            if (!isAllowedRoot) {
                throw IllegalArgumentException("Legacy groupPath is outside supported storage roots")
            }

            return normalizedPath
        }

        private fun org.json.JSONObject.optNormalizedString(key: String): String? {
            val rawValue = optString(key, "").trim()
            return rawValue.takeIf { it.isNotEmpty() }
        }
    }

/**
 * Result of migration operation.
 */
public sealed class MigrationResult {
    public data class Success(
        val booksCount: Int,
        val chaptersCount: Int,
        val legacyStateChecksum: String? = null,
        val preflightReport: MigrationPreflightReport? = null,
    ) : MigrationResult()

    public data class Failure(
        val error: Exception,
        val preflightReport: MigrationPreflightReport? = null,
        val rollbackReport: MigrationRollbackReport? = null,
    ) : MigrationResult()
}

public data class MigrationPreflightReport(
    val sourcePath: String,
    val sourceExists: Boolean,
    val sourceReadable: Boolean,
    val destinationPath: String,
    val destinationWritable: Boolean,
    val warnings: List<String>,
    val blockingIssues: List<String>,
) {
    val ready: Boolean = blockingIssues.isEmpty()
}

public data class MigrationRollbackReport(
    val attempted: Boolean,
    val succeeded: Boolean,
    val steps: List<String>,
)
