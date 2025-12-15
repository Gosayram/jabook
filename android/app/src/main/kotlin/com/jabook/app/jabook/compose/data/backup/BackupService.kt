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

package com.jabook.app.jabook.compose.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for backing up and restoring app data.
 * Exports/imports settings, book metadata, favorites, and search history.
 */
@Singleton
class BackupService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: JabookDatabase,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val protoSettingsRepository: ProtoSettingsRepository,
    ) {
        companion object {
            private const val TAG = "BackupService"
            private const val CURRENT_VERSION = "1.0.0"
        }

        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

        /**
         * Exports all app data to a JSON file.
         * Returns FileProvider URI for sharing.
         */
        suspend fun exportToFile(): Uri =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting data export")

                    // 1. Collect data
                    val backupData =
                        BackupData(
                            version = CURRENT_VERSION,
                            timestamp =
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                                    .format(Date()),
                            settings = collectSettings(),
                            bookMetadata = collectBookMetadata(),
                            favorites = emptyList(), // TODO: when favorites implemented
                            searchHistory = emptyList(), // TODO: when history implemented
                        )

                    // 2. Serialize to JSON
                    val jsonString = json.encodeToString(backupData)
                    Log.d(TAG, "Serialized backup: ${jsonString.length} bytes")

                    // 3. Write to file
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val fileName = "jabook_backup_$timestamp.json"
                    val file = File(context.cacheDir, fileName)
                    file.writeText(jsonString)

                    Log.d(TAG, "Backup written to ${file.absolutePath}")

                    // 4. Return FileProvider URI
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    throw e
                }
            }

        /**
         * Imports app data from a JSON backup file.
         * Returns statistics about imported items.
         */
        suspend fun importFromFile(uri: Uri): ImportStats =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting data import from $uri")

                    // 1. Read file
                    val jsonString =
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        } ?: throw IOException("Cannot read backup file")

                    Log.d(TAG, "Read backup file: ${jsonString.length} bytes")

                    // 2. Parse JSON
                    val backupData = json.decodeFromString<BackupData>(jsonString)
                    Log.d(TAG, "Parsed backup version ${backupData.version}")

                    // 3. Validate version
                    if (!isCompatibleVersion(backupData.version)) {
                        throw IllegalArgumentException(
                            "Incompatible backup version: ${backupData.version}. " +
                                "Expected version starting with '1.'",
                        )
                    }

                    // 4. Import data
                    val stats = ImportStats()

                    // Import settings
                    restoreSettings(backupData.settings)
                    stats.settingsRestored = true
                    Log.d(TAG, "Settings restored")

                    // Import book metadata (TODO: when Room entities ready)
                    backupData.bookMetadata.forEach { book ->
                        // TODO: database.bookDao().insert(book.toEntity())
                        Log.d(TAG, "Book metadata available: ${book.title}")
                    }
                    stats.booksRestored = backupData.bookMetadata.size

                    // Import favorites & history (TODO: when implemented)
                    stats.favoritesRestored = backupData.favorites.size
                    stats.historyRestored = backupData.searchHistory.size

                    Log.d(TAG, "Import complete: $stats")
                    stats
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    throw e
                }
            }

        /**
         * Collects current app settings.
         */
        private suspend fun collectSettings(): AppSettings {
            val userPrefs = userPreferencesRepository.userData.first()
            val protoSettings = protoSettingsRepository.userPreferences.first()

            return AppSettings(
                theme = userPrefs.theme.name,
                autoPlayNext = userPrefs.autoPlayNext,
                playbackSpeed = userPrefs.playbackSpeed,
                wifiOnlyDownload = protoSettings.wifiOnlyDownload,
                downloadPath = protoSettings.downloadPath,
                currentMirror = protoSettings.selectedMirror,
                autoSwitchMirror = protoSettings.autoSwitchMirror,
            )
        }

        /**
         * Collects book metadata from database.
         */
        private suspend fun collectBookMetadata(): List<BookBackup> {
            // TODO: Query Room database for books when entities ready
            // For now, return empty list
            return emptyList()
        }

        /**
         * Restores settings from backup.
         */
        private suspend fun restoreSettings(settings: AppSettings) {
            try {
                // Restore UserPreferences
                val theme = AppTheme.valueOf(settings.theme)
                userPreferencesRepository.setTheme(theme)
                userPreferencesRepository.setAutoPlayNext(settings.autoPlayNext)
                userPreferencesRepository.setPlaybackSpeed(settings.playbackSpeed)

                // Restore ProtoSettings
                protoSettingsRepository.updateWifiOnly(settings.wifiOnlyDownload)
                protoSettingsRepository.updateDownloadPath(settings.downloadPath)
                protoSettingsRepository.updateSelectedMirror(settings.currentMirror)
                protoSettingsRepository.updateAutoSwitchMirror(settings.autoSwitchMirror)

                Log.d(TAG, "All settings restored successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore some settings", e)
                throw e
            }
        }

        /**
         * Checks if backup version is compatible.
         * Currently accepts any 1.x.x version.
         */
        private fun isCompatibleVersion(version: String): Boolean = version.startsWith("1.")
    }
