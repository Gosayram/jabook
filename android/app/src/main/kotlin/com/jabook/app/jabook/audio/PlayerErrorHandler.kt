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

import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import com.jabook.app.jabook.util.LogUtils
import java.io.File

/**
 * Handles playback errors with automatic retry, skip, and recovery logic.
 *
 * Extracted from [PlayerListener] to isolate error handling responsibilities:
 * - Error logging with context (book, track, HTTP details)
 * - Retry with exponential backoff
 * - Skip to next available track
 * - File-not-found recovery
 *
 * @param getActivePlayer Function to get current ExoPlayer
 * @param getActualPlaylistSize Get actual playlist size
 * @param getCurrentMetadata Get current track metadata
 * @param getCurrentBookId Get current book identifier
 * @param scheduleNotificationUpdate Schedule notification refresh
 */
internal class PlayerErrorHandler(
    private val getActivePlayer: () -> Player,
    private val getActualPlaylistSize: () -> Int,
    private val getCurrentMetadata: () -> Map<String, String>?,
    private val getCurrentBookId: () -> String?,
    private val scheduleNotificationUpdate: () -> Unit = {},
) {
    private var retryCount = 0
    private var skipCount = 0
    private val maxRetries = 3
    private val maxSkips = 5
    private val retryDelayMs = 2000L

    /** Resets retry and skip counts on successful playback. */
    fun resetCounts() {
        retryCount = 0
        skipCount = 0
    }

    /** Logs detailed error context (HTTP, IO, book/track info). */
    fun logErrorContext(error: androidx.media3.common.PlaybackException) {
        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        val mediaId = player.currentMediaItem?.mediaId ?: "unknown"
        val metadata = getCurrentMetadata()
        val bookId = getCurrentBookId() ?: "unknown"
        val bookName = metadata?.get("title") ?: "unknown"

        LogUtils.e(TAG, "❌ Playback error: track=$currentIndex, mediaId=$mediaId, code=${error.errorCode}", error)
        LogUtils.e(TAG, "❌ Error context: bookId=$bookId, bookName=$bookName, chapterIdx=$currentIndex")

        val cause = error.cause
        when {
            cause is HttpDataSource.InvalidResponseCodeException -> {
                LogUtils.e(TAG, "❌ HTTP error: code=${cause.responseCode}, msg=${cause.responseMessage}")
                if (!cause.headerFields.isEmpty()) LogUtils.e(TAG, "❌ HTTP headers: ${cause.headerFields}")
            }
            cause is HttpDataSource.HttpDataSourceException -> LogUtils.e(TAG, "❌ HTTP data source error: type=${cause.type}")
            cause is java.io.IOException -> LogUtils.e(TAG, "❌ IO error: ${cause.message}")
        }
    }

    /**
     * Handles player error with retry/skip/rescan logic.
     * Returns the user-friendly error message.
     */
    fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        ErrorHandler.handlePlaybackError(TAG, error, "Player error during playback")

        val resolution =
            PlaybackErrorPolicy.resolve(
                errorCode = error.errorCode,
                hasRetriesLeft = retryCount < maxRetries,
                canSkipTrack = skipCount < maxSkips,
                fallbackMessage = error.message,
            )

        val userMessage =
            when (resolution.action) {
                PlaybackRecoveryAction.RETRY -> {
                    retryCount++
                    LogUtils.w(TAG, "${resolution.userMessage} ($retryCount/$maxRetries)")
                    val backoffDelay = retryDelayMs * retryCount
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val player = getActivePlayer()
                        player.prepare()
                        player.playWhenReady = true
                        LogUtils.d(TAG, "Retry $retryCount after error (delay: ${backoffDelay}ms)")
                    }, backoffDelay)
                    return
                }
                PlaybackRecoveryAction.SKIP_TRACK -> {
                    if (attemptSkipOnError()) resolution.userMessage
                    else "Playback error: Unable to recover automatically."
                }
                PlaybackRecoveryAction.RESCAN_LIBRARY -> "${resolution.userMessage} Try re-scanning your library."
                PlaybackRecoveryAction.NONE -> resolution.userMessage
            }

        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        val totalTracks = getActualPlaylistSize()
        val bookId = getCurrentBookId() ?: "unknown"
        val bookName = getCurrentMetadata()?.get("title") ?: "unknown"

        LogUtils.e(TAG, "❌ $userMessage (track=$currentIndex/$totalTracks, retry=$retryCount/$maxRetries, book=$bookId)")
        scheduleNotificationUpdate()
    }

    /** Attempts to skip to next track on error. Returns true if skip was initiated. */
    private fun attemptSkipOnError(): Boolean {
        return if (skipCount < maxSkips) {
            skipCount++
            LogUtils.w(TAG, "Skipping track due to error ($skipCount/$maxSkips)")
            handleFileNotFound()
            true
        } else {
            LogUtils.e(TAG, "Max skips reached ($maxSkips), stopping playback")
            false
        }
    }

    /** Skips to next track when current file is not found. */
    fun handleFileNotFound() {
        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex
        val totalTracks = getActualPlaylistSize()

        if (totalTracks <= 1) {
            LogUtils.w(TAG, "Cannot skip: only one track")
            return
        }
        if (currentIndex >= totalTracks - 1) {
            LogUtils.w(TAG, "Last track, cannot skip forward")
            player.playWhenReady = false
            return
        }

        val nextIndex = currentIndex + 1
        if (nextIndex < totalTracks) {
            LogUtils.w(TAG, "File not found at $currentIndex, skipping to $nextIndex")
            try {
                player.seekTo(nextIndex, 0L)
                if (player.isPlaying || player.playWhenReady) player.playWhenReady = true
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to skip to next track", e)
            }
        } else {
            LogUtils.w(TAG, "No more tracks, pausing")
            try { player.playWhenReady = false } catch (_: Exception) {}
        }
    }

    /** Skips to next available track when current is unavailable (file check). */
    fun skipToNextAvailableTrack(currentIndex: Int, previousIndex: Int) {
        val player = getActivePlayer()
        if (player.mediaItemCount <= 1) {
            LogUtils.w(TAG, "Cannot skip: only one track")
            return
        }

        val direction =
            if (currentIndex > previousIndex || (currentIndex == 0 && previousIndex == player.mediaItemCount - 1)) 1
            else -1

        var nextIndex = currentIndex
        var attempts = 0
        val maxAttempts = player.mediaItemCount

        while (attempts < maxAttempts) {
            nextIndex =
                when {
                    direction == 1 && nextIndex + 1 < player.mediaItemCount -> nextIndex + 1
                    direction == 1 -> { player.playWhenReady = false; return }
                    nextIndex - 1 >= 0 -> nextIndex - 1
                    else -> player.mediaItemCount - 1
                }

            val item = player.getMediaItemAt(nextIndex)
            val uri = item.localConfiguration?.uri
            if (uri != null) {
                val isAvailable =
                    when (uri.scheme) {
                        "file" -> { val f = File(uri.path ?: ""); f.exists() && f.canRead() }
                        "http", "https" -> true
                        else -> true
                    }
                if (isAvailable) {
                    LogUtils.d(TAG, "Found available track at $nextIndex")
                    try {
                        player.seekTo(nextIndex, 0L)
                        if (player.playWhenReady) player.playWhenReady = true
                        return
                    } catch (e: Exception) { LogUtils.e(TAG, "Failed to seek", e) }
                }
            }
            attempts++
        }
        LogUtils.w(TAG, "No available tracks found, pausing")
        try { player.playWhenReady = false } catch (_: Exception) {}
    }

    private companion object { private const val TAG = "AudioPlayerService" }
}
