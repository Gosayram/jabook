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

package com.jabook.app.jabook.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class PlayerPersistenceManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        public companion object {
            private const val PREFS_NAME = "FlutterSharedPreferences"
            private const val KEY_RESUMPTION_FILE_PATH = "playback_resumption_file_path"
            private const val KEY_RESUMPTION_POSITION_MS = "playback_resumption_position_ms"
            private const val KEY_RESUMPTION_DURATION_MS = "playback_resumption_duration_ms"
            private const val KEY_RESUMPTION_ARTWORK_PATH = "playback_resumption_artwork_path"
            private const val KEY_RESUMPTION_TITLE = "playback_resumption_title"
            private const val KEY_RESUMPTION_ARTIST = "playback_resumption_artist"
            private const val KEY_RESUMPTION_GROUP_PATH = "playback_resumption_group_path"
            private const val KEY_LAST_PLAYED_BOOK_ID = "last_played_book_id" // NEW for mini player
            private const val LEGACY_KEY_PLAYER_STATE = "flutter.player_state"
            private const val PLAYBACK_SNAPSHOT_VERSION_V1 = 1
            private const val KEY_PLAYBACK_SNAPSHOT_VERSION = "playback_snapshot_version"
            private const val KEY_PLAYBACK_SNAPSHOT_GROUP_PATH = "playback_snapshot_group_path"
            private const val KEY_PLAYBACK_SNAPSHOT_FILE_PATHS = "playback_snapshot_file_paths"
            private const val KEY_PLAYBACK_SNAPSHOT_CURRENT_INDEX = "playback_snapshot_current_index"
            private const val KEY_PLAYBACK_SNAPSHOT_CURRENT_POSITION = "playback_snapshot_current_position"
            private const val KEY_PLAYBACK_SNAPSHOT_METADATA = "playback_snapshot_metadata"
            private const val KEY_PLAYBACK_SNAPSHOT_CORRUPTION_COUNT = "playback_snapshot_corruption_count"
            private const val KEY_PLAYBACK_SNAPSHOT_LAST_CORRUPTION_REASON = "playback_snapshot_last_corruption_reason"
            private const val KEY_PLAYBACK_SNAPSHOT_LAST_CORRUPTION_AT = "playback_snapshot_last_corruption_at"
        }

        // NEW: StateFlow for last played book ID (for mini player)
        private val _lastPlayedBookId = MutableStateFlow<String?>(null)
        public val lastPlayedBookId: StateFlow<String?> = _lastPlayedBookId.asStateFlow()

        init {
            // Load last played book ID on init
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _lastPlayedBookId.value = prefs.getString(KEY_LAST_PLAYED_BOOK_ID, null)
        }

        public data class PersistedPlayerState(
            val groupPath: String,
            val filePaths: List<String>,
            val currentIndex: Int,
            val currentPosition: Long,
            val metadata: Map<String, String>?,
        )

        public suspend fun saveCurrentMediaItem(
            mediaId: String,
            positionMs: Long,
            durationMs: Long,
            artworkPath: String,
            title: String,
            artist: String,
            groupPath: String,
        ): Unit =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs
                        .edit()
                        .putString(KEY_RESUMPTION_FILE_PATH, mediaId)
                        .putLong(KEY_RESUMPTION_POSITION_MS, positionMs)
                        .putLong(KEY_RESUMPTION_DURATION_MS, durationMs)
                        .putString(KEY_RESUMPTION_ARTWORK_PATH, artworkPath)
                        .putString(KEY_RESUMPTION_TITLE, title)
                        .putString(KEY_RESUMPTION_ARTIST, artist)
                        .putString(KEY_RESUMPTION_GROUP_PATH, groupPath)
                        .apply()
                    android.util.Log.v(
                        "PlayerPersistence",
                        "Stored current media item for resumption: $mediaId, position=${positionMs}ms",
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PlayerPersistence", "Failed to store current media item for resumption", e)
                }
            }

        public suspend fun retrieveLastStoredMediaItem(): Map<String, Any?>? =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val filePath = prefs.getString(KEY_RESUMPTION_FILE_PATH, null) ?: return@withContext null
                    val positionMs = prefs.getLong(KEY_RESUMPTION_POSITION_MS, 0L)
                    val durationMs = prefs.getLong(KEY_RESUMPTION_DURATION_MS, 0L)
                    val artworkPath = prefs.getString(KEY_RESUMPTION_ARTWORK_PATH, "")
                    val title = prefs.getString(KEY_RESUMPTION_TITLE, "")
                    val artist = prefs.getString(KEY_RESUMPTION_ARTIST, "")
                    val groupPath = prefs.getString(KEY_RESUMPTION_GROUP_PATH, "")

                    mapOf(
                        "filePath" to filePath,
                        "positionMs" to positionMs,
                        "durationMs" to durationMs,
                        "artworkPath" to artworkPath,
                        "title" to title,
                        "artist" to artist,
                        "groupPath" to groupPath,
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PlayerPersistence", "Failed to retrieve last stored media item", e)
                    null
                }
            }

        public suspend fun retrievePersistedPlayerState(): PersistedPlayerState? =
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                readVersionedSnapshot(prefs)?.let { return@withContext it }

                val legacyJson = prefs.getString(LEGACY_KEY_PLAYER_STATE, null) ?: return@withContext null
                val legacyState =
                    runCatching { parseLegacySnapshot(legacyJson) }
                        .getOrElse { error ->
                            recordCorruption(
                                prefs = prefs,
                                reason = "legacy_snapshot_parse_failed",
                                error = error,
                            )
                            prefs.edit().remove(LEGACY_KEY_PLAYER_STATE).apply()
                            return@withContext null
                        }

                saveVersionedSnapshot(prefs = prefs, state = legacyState)
                legacyState
            }

        public suspend fun savePersistedPlayerState(state: PersistedPlayerState): Unit =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    saveVersionedSnapshot(prefs = prefs, state = state)
                } catch (e: Exception) {
                    android.util.Log.w("PlayerPersistence", "Failed to save persisted player snapshot", e)
                }
            }

        public fun saveGroupPathToSharedPreferences(groupPath: String) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val sanitizedPath = this.sanitizeGroupPath(groupPath)
                prefs.edit().putString("current_group_path", sanitizedPath).apply()
                android.util.Log.d("PlayerPersistence", "Saved groupPath to SharedPreferences: $sanitizedPath")
            } catch (e: Exception) {
                android.util.Log.w("PlayerPersistence", "Failed to save groupPath to SharedPreferences", e)
            }
        }

        // NEW: Per-book state for Backup/Restore & Activity Sorting
        public suspend fun savePlayerState(state: PlayerState): Unit =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val json =
                        JSONObject().apply {
                            put("bookId", state.bookId)
                            put("positionMs", state.positionMs)
                            put("durationMs", state.durationMs)
                            put("lastPlayedTimestamp", state.lastPlayedTimestamp)
                            put("completedTimestamp", state.completedTimestamp)
                            put("playCount", state.playCount)
                            // We don't necessarily need filePaths in this light state
                        }
                    prefs.edit().putString("book_state_${state.bookId}", json.toString()).apply()
                } catch (e: Exception) {
                    android.util.Log.e("PlayerPersistence", "Failed to save player state for ${state.bookId}", e)
                }
            }

        public suspend fun getPlayerState(bookId: String): PlayerState? =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString = prefs.getString("book_state_$bookId", null) ?: return@withContext null
                    val json = JSONObject(jsonString)

                    PlayerState(
                        bookId = json.getString("bookId"),
                        positionMs = json.optLong("positionMs", 0),
                        durationMs = json.optLong("durationMs", 0),
                        filePaths = emptyList(), // Not stored here
                        lastPlayedTimestamp = json.optLong("lastPlayedTimestamp", 0),
                        completedTimestamp = json.optLong("completedTimestamp", 0),
                        playCount = json.optLong("playCount", 0),
                    )
                } catch (e: Exception) {
                    android.util.Log.e("PlayerPersistence", "Failed to get player state for $bookId", e)
                    null
                }
            }

        public suspend fun updateLastPlayed(bookId: String) {
            val currentState = getPlayerState(bookId)
            val newState =
                if (currentState != null) {
                    currentState.copy(lastPlayedTimestamp = System.currentTimeMillis())
                } else {
                    PlayerState(
                        bookId = bookId,
                        positionMs = 0,
                        durationMs = 0,
                        filePaths = emptyList(),
                        lastPlayedTimestamp = System.currentTimeMillis(),
                    )
                }
            savePlayerState(newState)

            // NEW: Update last played book ID for mini player
            _lastPlayedBookId.value = bookId
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_PLAYED_BOOK_ID, bookId).apply()
        }

        public suspend fun markCompleted(bookId: String) {
            val currentState = getPlayerState(bookId)
            if (currentState != null && currentState.completedTimestamp == 0L) {
                savePlayerState(currentState.copy(completedTimestamp = System.currentTimeMillis()))
            } else if (currentState == null) {
                // edge case: completed but never played? unlikely but possible
                savePlayerState(
                    PlayerState(
                        bookId = bookId,
                        positionMs = 0,
                        durationMs = 0,
                        filePaths = emptyList(),
                        completedTimestamp = System.currentTimeMillis(),
                    ),
                )
            }
        }

        /**
         * Increments the play count for a book.
         * Should be called when a book/chapter is completed.
         */
        public suspend fun incrementPlayCount(bookId: String) {
            val currentState = getPlayerState(bookId)
            val newState =
                if (currentState != null) {
                    currentState.copy(playCount = currentState.playCount + 1)
                } else {
                    PlayerState(
                        bookId = bookId,
                        positionMs = 0,
                        durationMs = 0,
                        filePaths = emptyList(),
                        playCount = 1,
                    )
                }
            savePlayerState(newState)
            android.util.Log.d("PlayerPersistence", "Play count for $bookId incremented to ${newState.playCount}")
        }

        /**
         * Gets the play count for a book.
         */
        public suspend fun getPlayCount(bookId: String): Int {
            val state = getPlayerState(bookId)
            return (state?.playCount ?: 0L).toInt()
        }

        private fun sanitizeGroupPath(path: String): String = path.replace(Regex("[^\\w\\-.]"), "_")

        private fun readVersionedSnapshot(prefs: android.content.SharedPreferences): PersistedPlayerState? {
            val storedVersion = prefs.getInt(KEY_PLAYBACK_SNAPSHOT_VERSION, 0)
            if (storedVersion <= 0) {
                return null
            }

            if (storedVersion != PLAYBACK_SNAPSHOT_VERSION_V1) {
                recordCorruption(
                    prefs = prefs,
                    reason = "unsupported_snapshot_version_$storedVersion",
                    error = null,
                )
                clearVersionedSnapshot(prefs)
                return null
            }

            return runCatching {
                val groupPath = prefs.getString(KEY_PLAYBACK_SNAPSHOT_GROUP_PATH, null).orEmpty()
                if (groupPath.isEmpty()) {
                    error("groupPath is missing")
                }

                val filePathsJson = prefs.getString(KEY_PLAYBACK_SNAPSHOT_FILE_PATHS, null).orEmpty()
                if (filePathsJson.isEmpty()) {
                    error("filePaths is missing")
                }

                val filePathsArray = JSONArray(filePathsJson)
                val filePaths =
                    buildList {
                        for (i in 0 until filePathsArray.length()) {
                            add(filePathsArray.getString(i))
                        }
                    }
                if (filePaths.isEmpty()) {
                    error("filePaths is empty")
                }

                val metadataJson = prefs.getString(KEY_PLAYBACK_SNAPSHOT_METADATA, null)
                val metadata = parseMetadataJson(metadataJson)

                PersistedPlayerState(
                    groupPath = groupPath,
                    filePaths = filePaths,
                    currentIndex = prefs.getInt(KEY_PLAYBACK_SNAPSHOT_CURRENT_INDEX, 0),
                    currentPosition = prefs.getLong(KEY_PLAYBACK_SNAPSHOT_CURRENT_POSITION, 0L),
                    metadata = metadata,
                )
            }.getOrElse { error ->
                recordCorruption(
                    prefs = prefs,
                    reason = "versioned_snapshot_parse_failed",
                    error = error,
                )
                clearVersionedSnapshot(prefs)
                null
            }
        }

        private fun saveVersionedSnapshot(
            prefs: android.content.SharedPreferences,
            state: PersistedPlayerState,
        ) {
            val metadataJson =
                state.metadata
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { JSONObject(it).toString() }
            prefs
                .edit()
                .putInt(KEY_PLAYBACK_SNAPSHOT_VERSION, PLAYBACK_SNAPSHOT_VERSION_V1)
                .putString(KEY_PLAYBACK_SNAPSHOT_GROUP_PATH, state.groupPath)
                .putString(KEY_PLAYBACK_SNAPSHOT_FILE_PATHS, JSONArray(state.filePaths).toString())
                .putInt(KEY_PLAYBACK_SNAPSHOT_CURRENT_INDEX, state.currentIndex)
                .putLong(KEY_PLAYBACK_SNAPSHOT_CURRENT_POSITION, state.currentPosition)
                .putString(KEY_PLAYBACK_SNAPSHOT_METADATA, metadataJson)
                .apply()
        }

        private fun clearVersionedSnapshot(prefs: android.content.SharedPreferences) {
            prefs
                .edit()
                .remove(KEY_PLAYBACK_SNAPSHOT_VERSION)
                .remove(KEY_PLAYBACK_SNAPSHOT_GROUP_PATH)
                .remove(KEY_PLAYBACK_SNAPSHOT_FILE_PATHS)
                .remove(KEY_PLAYBACK_SNAPSHOT_CURRENT_INDEX)
                .remove(KEY_PLAYBACK_SNAPSHOT_CURRENT_POSITION)
                .remove(KEY_PLAYBACK_SNAPSHOT_METADATA)
                .apply()
        }

        private fun parseLegacySnapshot(legacyJson: String): PersistedPlayerState {
            val json = JSONObject(legacyJson)
            val groupPath = json.getString("groupPath")
            val currentIndex = json.optInt("currentIndex", 0)
            val currentPosition = json.optLong("currentPosition", 0L)
            val filePathsJson = json.getJSONArray("filePaths")
            val filePaths =
                buildList {
                    for (i in 0 until filePathsJson.length()) {
                        add(filePathsJson.getString(i))
                    }
                }
            if (filePaths.isEmpty()) {
                error("legacy snapshot contains empty filePaths")
            }

            return PersistedPlayerState(
                groupPath = groupPath,
                filePaths = filePaths,
                currentIndex = currentIndex,
                currentPosition = currentPosition,
                metadata = parseMetadataJson(json.optJSONObject("metadata")?.toString()),
            )
        }

        private fun parseMetadataJson(metadataJson: String?): Map<String, String>? {
            if (metadataJson.isNullOrBlank()) {
                return null
            }
            val metadataObject = JSONObject(metadataJson)
            val metadata = mutableMapOf<String, String>()
            val keys = metadataObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                metadata[key] = metadataObject.getString(key)
            }
            return metadata.ifEmpty { null }
        }

        private fun recordCorruption(
            prefs: android.content.SharedPreferences,
            reason: String,
            error: Throwable?,
        ) {
            val count = prefs.getInt(KEY_PLAYBACK_SNAPSHOT_CORRUPTION_COUNT, 0) + 1
            prefs
                .edit()
                .putInt(KEY_PLAYBACK_SNAPSHOT_CORRUPTION_COUNT, count)
                .putString(KEY_PLAYBACK_SNAPSHOT_LAST_CORRUPTION_REASON, reason)
                .putLong(KEY_PLAYBACK_SNAPSHOT_LAST_CORRUPTION_AT, System.currentTimeMillis())
                .apply()
            android.util.Log.w("PlayerPersistence", "Playback snapshot corruption detected: $reason", error)
        }
    }

/**
 * Lightweight state for backup and sorting
 */
public data class PlayerState(
    val bookId: String,
    val positionMs: Long,
    val durationMs: Long,
    val filePaths: List<String>,
    val lastPlayedTimestamp: Long = 0L,
    val completedTimestamp: Long = 0L,
    val playCount: Long = 0L,
)
