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
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tracks book completion by monitoring playback position on the last track.
 *
 * Extracted from [PlayerListener] to isolate the complex position-checking logic
 * that detects when a book has been fully listened to.
 *
 * @param context Context for sending completion broadcast
 * @param scope Coroutine scope for position checking
 * @param getActivePlayer Function to get current ExoPlayer
 * @param getIsBookCompleted Check if book is already completed
 * @param setIsBookCompleted Mark book as completed
 * @param getActualPlaylistSize Get actual playlist size
 * @param getLastCompletedTrackIndex Get previously saved completion index
 * @param setLastCompletedTrackIndex Save completion index
 * @param saveCurrentPosition Save current playback position
 * @param getCurrentBookId Get current book identifier
 * @param markBookCompleted Mark book as completed in database
 * @param scheduleNotificationUpdate Schedule notification refresh
 */
internal class BookCompletionTracker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getActivePlayer: () -> Player,
    private val getIsBookCompleted: () -> Boolean,
    private val setIsBookCompleted: (Boolean) -> Unit,
    private val getActualPlaylistSize: () -> Int,
    private val getLastCompletedTrackIndex: () -> Int,
    private val setLastCompletedTrackIndex: (Int) -> Unit,
    private val saveCurrentPosition: () -> Unit,
    private val getCurrentBookId: () -> String?,
    private val markBookCompleted: ((String) -> Unit)?,
    private val scheduleNotificationUpdate: () -> Unit,
) {
    private var positionCheckJob: Job? = null
    private var lastPosition: Long = -1L
    private var positionStoppedCount: Int = 0
    private var positionStoppedStartTime: Long = -1L

    private val positionCheckIntervalMs: Long = 1000L
    private val positionStoppedThreshold: Int = 3
    private val maxPositionStoppedTimeMs: Long = 10_000L
    private val smartCompletionThresholdMs: Long = 180_000L

    /** Starts periodic position checking for book completion detection. */
    fun startPositionCheck() {
        stopPositionCheck()
        val player = getActivePlayer()
        LogUtils.i(TAG, "Starting position check: index=${player.currentMediaItemIndex}/${getActualPlaylistSize()}")

        positionCheckJob =
            scope.launch {
                while (coroutineContext.isActive) {
                    if (getIsBookCompleted()) break
                    val player = getActivePlayer()
                    if (player.playbackState == Player.STATE_ENDED) break
                    val currentIndex = player.currentMediaItemIndex
                    val totalTracks = getActualPlaylistSize()
                    if (!player.isPlaying && currentIndex < totalTracks - 1) break
                    val currentPosition = player.currentPosition
                    val duration = player.duration
                    val eofThresholdMs = EndOfFileDetectionPolicy.calculateThresholdMs(duration)

                    if (currentIndex >= totalTracks - 1) {
                        if (duration != C.TIME_UNSET && duration > 0) {
                            if (currentPosition >= duration) { handleBookCompletion(player, currentIndex); break }
                            val remaining = duration - currentPosition
                            if (remaining in (eofThresholdMs + 1)..smartCompletionThresholdMs) { handleBookCompletion(player, currentIndex); break }
                            if (remaining <= eofThresholdMs) { handleBookCompletion(player, currentIndex); break }
                        }
                        if (checkPositionStopped(player, currentPosition, duration, eofThresholdMs)) { handleBookCompletion(player, currentIndex); break }
                        lastPosition = currentPosition
                    } else { resetPositionTrackingState() }

                    if (currentIndex >= totalTracks - 1 && player.playbackState == Player.STATE_READY && !player.isPlaying && !player.playWhenReady) {
                        val isNearEnd = duration != C.TIME_UNSET && duration > 0 && currentPosition >= duration - eofThresholdMs
                        val posStopped = lastPosition >= 0 && currentPosition == lastPosition && positionStoppedCount >= 1
                        if (isNearEnd || posStopped) { handleBookCompletion(player, currentIndex); break }
                    }
                    kotlinx.coroutines.delay(positionCheckIntervalMs)
                }
                resetPositionTrackingState()
            }
    }

    /** Stops periodic position checking. */
    fun stopPositionCheck() { positionCheckJob?.cancel(); positionCheckJob = null; resetPositionTrackingState() }

    /** Release all resources. */
    fun release() { stopPositionCheck() }

    /** Handles book completion. Public so [PlayerListener] can delegate. */
    fun handleBookCompletion(player: Player, currentIndex: Int, source: String = "position_check"): Boolean {
        if (getIsBookCompleted()) return false
        val totalTracks = getActualPlaylistSize()
        if (totalTracks <= 0) return false

        val savedIndex = getLastCompletedTrackIndex()
        val actualIndex = BookCompletionIndexPolicy.resolveCompletionIndex(currentIndex, totalTracks, savedIndex, player.currentPosition, player.duration)
        if (actualIndex < totalTracks - 1) return false

        val lastIndex = actualIndex
        val lastPos = player.currentPosition
        LogUtils.i(TAG, "Book completed from $source: track $lastIndex of ${totalTracks - 1}, pos=${lastPos}ms")
        setIsBookCompleted(true)
        getCurrentBookId()?.let { markBookCompleted?.invoke(it) }
        setLastCompletedTrackIndex(lastIndex)

        try {
            player.pause(); player.playWhenReady = false
            if (lastIndex in 0 until totalTracks) player.seekTo(lastIndex, if (lastPos > 0) lastPos else Long.MAX_VALUE)
        } catch (e: Exception) { LogUtils.e(TAG, "Error handling completion", e); player.playWhenReady = false }

        saveCurrentPosition(); scheduleNotificationUpdate()
        context.sendBroadcast(Intent("com.jabook.app.jabook.BOOK_COMPLETED").apply { setPackage(context.packageName); putExtra("last_track_index", lastIndex) })
        return true
    }

    private fun checkPositionStopped(player: Player, currentPosition: Long, duration: Long, eofThresholdMs: Long): Boolean {
        if (lastPosition < 0 || currentPosition != lastPosition) { positionStoppedCount = 0; positionStoppedStartTime = -1L; return false }
        val currentTime = System.currentTimeMillis()
        if (positionStoppedStartTime < 0) positionStoppedStartTime = currentTime
        val wasPlayingRecently = player.isPlaying || player.playWhenReady
        val isNearEnd = duration != C.TIME_UNSET && duration > 0 && currentPosition >= duration - (eofThresholdMs * 2)
        val stoppedTimeMs = currentTime - positionStoppedStartTime
        if (wasPlayingRecently || isNearEnd || stoppedTimeMs >= maxPositionStoppedTimeMs) {
            positionStoppedCount++
            return positionStoppedCount >= positionStoppedThreshold || stoppedTimeMs >= maxPositionStoppedTimeMs
        }
        positionStoppedCount = 0; positionStoppedStartTime = -1L; return false
    }

    private fun resetPositionTrackingState() { lastPosition = -1L; positionStoppedCount = 0; positionStoppedStartTime = -1L }

    private companion object { private const val TAG = "BookCompletion" }
}