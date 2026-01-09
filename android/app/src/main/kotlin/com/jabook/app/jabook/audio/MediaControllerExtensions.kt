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

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Extension functions for MediaController to send custom commands.
 * Provides type-safe methods for accessing service functionality through MediaController.
 */
object MediaControllerExtensions {
    /**
     * Sets playlist through MediaController custom command.
     * This is more reliable than using getInstance().
     *
     * @param controller MediaController instance
     * @param filePaths List of file paths
     * @param metadata Optional metadata map
     * @param initialTrackIndex Optional initial track index
     * @param initialPosition Optional initial position in milliseconds
     * @param groupPath Optional group path (book ID)
     * @return Future with result
     */
    fun setPlaylist(
        controller: MediaController,
        filePaths: List<String>,
        metadata: Map<String, String>? = null,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        groupPath: String? = null,
    ): ListenableFuture<SessionResult> {
        val args =
            Bundle().apply {
                putStringArray(
                    AudioPlayerLibrarySessionCallback.ARG_FILE_PATHS,
                    filePaths.toTypedArray(),
                )
                if (metadata != null) {
                    val metadataBundle = Bundle()
                    metadata.forEach { (key, value) ->
                        metadataBundle.putString(key, value)
                    }
                    putBundle(AudioPlayerLibrarySessionCallback.ARG_METADATA, metadataBundle)
                }
                if (initialTrackIndex != null) {
                    putInt(AudioPlayerLibrarySessionCallback.ARG_INITIAL_TRACK_INDEX, initialTrackIndex)
                }
                if (initialPosition != null) {
                    putLong(AudioPlayerLibrarySessionCallback.ARG_INITIAL_POSITION, initialPosition)
                }
                if (groupPath != null) {
                    putString(AudioPlayerLibrarySessionCallback.ARG_GROUP_PATH, groupPath)
                }
            }

        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_PLAYLIST,
                args,
            )
        return controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    /**
     * Sets sleep timer with duration in minutes.
     */
    fun setSleepTimerMinutes(
        controller: MediaController,
        minutes: Int,
    ): ListenableFuture<SessionResult> {
        val args =
            Bundle().apply {
                putInt(AudioPlayerLibrarySessionCallback.ARG_MINUTES, minutes)
            }
        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_SLEEP_TIMER_MINUTES,
                args,
            )
        return controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    /**
     * Sets sleep timer to end of chapter.
     */
    fun setSleepTimerEndOfChapter(controller: MediaController): ListenableFuture<SessionResult> {
        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_CHAPTER,
                Bundle.EMPTY,
            )
        return controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    /**
     * Cancels sleep timer.
     */
    fun cancelSleepTimer(controller: MediaController): ListenableFuture<SessionResult> {
        val command =
            SessionCommand(
                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
                Bundle.EMPTY,
            )
        return controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    /**
     * Gets sleep timer remaining seconds.
     * Returns null if timer is not active or set to end of chapter.
     */
    suspend fun getSleepTimerRemainingSeconds(controller: MediaController): Int? =
        withContext(Dispatchers.IO) {
            try {
                val command =
                    SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_GET_SLEEP_TIMER_REMAINING,
                        Bundle.EMPTY,
                    )
                val future = controller.sendCustomCommand(command, Bundle.EMPTY)
                val result = future.get(MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    if (result.extras.containsKey(AudioPlayerLibrarySessionCallback.ARG_RESULT_REMAINING)) {
                        result.extras.getInt(AudioPlayerLibrarySessionCallback.ARG_RESULT_REMAINING)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("MediaControllerExtensions", "Failed to get sleep timer remaining", e)
                null
            }
        }

    /**
     * Checks if sleep timer is active.
     */
    suspend fun isSleepTimerActive(controller: MediaController): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val command =
                    SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_IS_SLEEP_TIMER_ACTIVE,
                        Bundle.EMPTY,
                    )
                val future = controller.sendCustomCommand(command, Bundle.EMPTY)
                val result = future.get(MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    result.extras.getBoolean(AudioPlayerLibrarySessionCallback.ARG_RESULT_ACTIVE, false)
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.w("MediaControllerExtensions", "Failed to check sleep timer active", e)
                false
            }
        }

    /**
     * Checks if sleep timer is set to end of chapter.
     */
    suspend fun isSleepTimerEndOfChapter(controller: MediaController): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val command =
                    SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_IS_SLEEP_TIMER_END_OF_CHAPTER,
                        Bundle.EMPTY,
                    )
                val future = controller.sendCustomCommand(command, Bundle.EMPTY)
                val result = future.get(MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    result.extras.getBoolean(AudioPlayerLibrarySessionCallback.ARG_RESULT_END_OF_CHAPTER, false)
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.w("MediaControllerExtensions", "Failed to check sleep timer end of chapter", e)
                false
            }
        }

    /**
     * Gets current group path (book ID).
     */
    suspend fun getCurrentGroupPath(controller: MediaController): String? =
        withContext(Dispatchers.IO) {
            try {
                val command =
                    SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_GET_CURRENT_GROUP_PATH,
                        Bundle.EMPTY,
                    )
                val future = controller.sendCustomCommand(command, Bundle.EMPTY)
                val result = future.get(MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    result.extras.getString(AudioPlayerLibrarySessionCallback.ARG_RESULT_GROUP_PATH)
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("MediaControllerExtensions", "Failed to get current group path", e)
                null
            }
        }

    /**
     * Gets current file paths (playlist).
     */
    suspend fun getCurrentFilePaths(controller: MediaController): List<String>? =
        withContext(Dispatchers.IO) {
            try {
                val command =
                    SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_GET_CURRENT_FILE_PATHS,
                        Bundle.EMPTY,
                    )
                val future = controller.sendCustomCommand(command, Bundle.EMPTY)
                val result = future.get(MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    result.extras.getStringArray(AudioPlayerLibrarySessionCallback.ARG_RESULT_FILE_PATHS)?.toList()
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("MediaControllerExtensions", "Failed to get current file paths", e)
                null
            }
        }
}
