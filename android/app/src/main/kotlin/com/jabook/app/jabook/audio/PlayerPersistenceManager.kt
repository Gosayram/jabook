package com.jabook.app.jabook.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerPersistenceManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val PREFS_NAME = "FlutterSharedPreferences"
            private const val KEY_RESUMPTION_FILE_PATH = "playback_resumption_file_path"
            private const val KEY_RESUMPTION_POSITION_MS = "playback_resumption_position_ms"
            private const val KEY_RESUMPTION_DURATION_MS = "playback_resumption_duration_ms"
            private const val KEY_RESUMPTION_ARTWORK_PATH = "playback_resumption_artwork_path"
            private const val KEY_RESUMPTION_TITLE = "playback_resumption_title"
            private const val KEY_RESUMPTION_ARTIST = "playback_resumption_artist"
            private const val KEY_RESUMPTION_GROUP_PATH = "playback_resumption_group_path"
        }

        data class PersistedPlayerState(
            val groupPath: String,
            val filePaths: List<String>,
            val currentIndex: Int,
            val currentPosition: Long,
            val metadata: Map<String, String>?,
        )

        suspend fun saveCurrentMediaItem(
            mediaId: String,
            positionMs: Long,
            durationMs: Long,
            artworkPath: String,
            title: String,
            artist: String,
            groupPath: String,
        ) = withContext(Dispatchers.IO) {
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

        suspend fun retrieveLastStoredMediaItem(): Map<String, Any?>? =
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

        suspend fun retrievePersistedPlayerState(): PersistedPlayerState? =
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val jsonString = prefs.getString("flutter.player_state", null) ?: return@withContext null

                    val json = JSONObject(jsonString)
                    val groupPath = json.getString("groupPath")
                    val currentIndex = json.optInt("currentIndex", 0)
                    val currentPosition = json.optLong("currentPosition", 0L)

                    val filePathsJson = json.getJSONArray("filePaths")
                    val filePaths = mutableListOf<String>()
                    for (i in 0 until filePathsJson.length()) {
                        filePaths.add(filePathsJson.getString(i))
                    }

                    val metadataJson = json.optJSONObject("metadata")
                    val metadata =
                        if (metadataJson != null) {
                            val map = mutableMapOf<String, String>()
                            val keys = metadataJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                map[key] = metadataJson.getString(key)
                            }
                            map
                        } else {
                            null
                        }

                    PersistedPlayerState(groupPath, filePaths, currentIndex, currentPosition, metadata)
                } catch (e: Exception) {
                    android.util.Log.e("PlayerPersistence", "Failed to parse player_state JSON", e)
                    null
                }
            }

        fun saveGroupPathToSharedPreferences(groupPath: String) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val sanitizedPath = sanitizeGroupPath(groupPath)
                prefs.edit().putString("current_group_path", sanitizedPath).apply()
                android.util.Log.d("PlayerPersistence", "Saved groupPath to SharedPreferences: $sanitizedPath")
            } catch (e: Exception) {
                android.util.Log.w("PlayerPersistence", "Failed to save groupPath to SharedPreferences", e)
            }
        }

        private fun sanitizeGroupPath(path: String): String = path.replace(Regex("[^\\w\\-.]"), "_")
    }
