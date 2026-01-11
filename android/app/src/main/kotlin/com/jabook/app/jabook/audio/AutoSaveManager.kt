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

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages automatic periodic saving of playback state.
 *
 * Inspired by InnerTune's approach: saves queue every 30 seconds to prevent
 * data loss from crashes or force kills.
 *
 * Features:
 * - Periodic auto-save every [AUTO_SAVE_INTERVAL_MS] milliseconds
 * - Save on significant events (pause, track change, seek)
 * - Debounced saves to avoid excessive disk writes
 *
 * Usage:
 * ```
 * autoSaveManager.startAutoSave { getCurrentState() }
 * // ... later
 * autoSaveManager.stopAutoSave()
 * ```
 */
@Singleton
public class AutoSaveManager
    @Inject
    public constructor(
        private val persistenceManager: PlayerPersistenceManager,
    ) {
        public companion object {
            private const val TAG = "AutoSaveManager"

            /** Interval between auto-saves in milliseconds (30 seconds) */
            public const val AUTO_SAVE_INTERVAL_MS: Int = _000L

            /** Minimum interval between saves to avoid excessive writes */
            public const val MIN_SAVE_INTERVAL_MS: Int = _000L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var autoSaveJob: Job? = null
        private var lastSaveTime = 0L

        /**
         * Starts periodic auto-save of playback state.
         *
         * @param stateProvider Lambda that provides current playback state.
         *                       Called every [AUTO_SAVE_INTERVAL_MS] to get fresh state.
         */
        public fun startAutoSave(stateProvider: suspend () -> PlaybackSnapshot?) {
            stopAutoSave() // Cancel any existing job

            autoSaveJob =
                scope.launch {
                    Log.d(TAG, "Auto-save started")
                    while (isActive) {
                        delay(AUTO_SAVE_INTERVAL_MS)
                        try {
                            val snapshot = stateProvider()
                            if (snapshot != null) {
                                saveSnapshot(snapshot)
                                Log.v(TAG, "Auto-saved state: ${snapshot.mediaId}, position=${snapshot.positionMs}ms")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Auto-save failed", e)
                        }
                    }
                }
        }

        /**
         * Stops periodic auto-save.
         * Should be called when playback stops or service is destroyed.
         */
        public fun stopAutoSave(...) {
            autoSaveJob?.cancel()
            autoSaveJob = null
            Log.d(TAG, "Auto-save stopped")
        }

        /**
         * Immediately saves current state (debounced).
         * Use for important events like pause, track change, or seek.
         *
         * @param snapshot Current playback state to save
         * @param force If true, ignores debounce interval
         */
        public suspend fun saveNow(
            snapshot: PlaybackSnapshot,
            force: Boolean = false,
        ) {
            val now = System.currentTimeMillis()
            if (!force && (now - lastSaveTime) < MIN_SAVE_INTERVAL_MS) {
                Log.v(TAG, "Save skipped (debounced)")
                return
            }

            saveSnapshot(snapshot)
            lastSaveTime = now
            Log.d(TAG, "Saved state immediately: ${snapshot.mediaId}, position=${snapshot.positionMs}ms")
        }

        private suspend fun saveSnapshot(snapshot: PlaybackSnapshot) {
            persistenceManager.saveCurrentMediaItem(
                mediaId = snapshot.mediaId,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                artworkPath = snapshot.artworkPath,
                title = snapshot.title,
                artist = snapshot.artist,
                groupPath = snapshot.groupPath,
            )
            lastSaveTime = System.currentTimeMillis()
        }

        /**
         * Checks if auto-save is currently running.
         */
        public fun isRunning(): Boolean = autoSaveJob?.isActive == true
    }

/**
 * Snapshot of current playback state for persistence.
 */
public data class PlaybackSnapshot(
    public val mediaId: String,
    public val positionMs: Long,
    public val durationMs: Long,
    public val artworkPath: String,
    public val title: String,
    public val artist: String,
    public val groupPath: String,
)
