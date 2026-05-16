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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DynamicPriorityRebalancer recalculates loading priorities when user rapidly navigates
 * between chapters (large jumps). Prevents playback buffering by rebalancing priorities.
 *
 * P-10: When user quickly flips through chapters (jumps 10+ chapters), the priority
 * policy doesn't recalculate — playback buffers. This class monitors chapter jumps
 * and triggers priority rebalancing.
 */
class DynamicPriorityRebalancer(
    private val playlistManager: PlaylistManager,
    private val scope: CoroutineScope,
) {
    private var lastKnownIndex = 0
    private var rebalanceJob: Job? = null
    private val rebalanceMutex = Mutex()

    /**
     * Call when user jumps to a new chapter index.
     * If the jump is large (>= 5 chapters), rebalance priorities around the new index.
     */
    fun onChapterJump(newIndex: Int) {
        val delta = kotlin.math.abs(newIndex - lastKnownIndex)
        lastKnownIndex = newIndex

        if (delta >= 5) {
            // Large jump - cancel current background loading and restart with new center
            rebalanceJob?.cancel()
            rebalanceJob =
                scope.launch {
                    // Debounce: wait to see if user continues jumping
                    delay(150)
                    rebalancePrioritiesAround(newIndex)
                }
        }
    }

    private suspend fun rebalancePrioritiesAround(targetIndex: Int) {
        playlistManager.apply {
            // Cancel any ongoing loading job to prevent stale loading
            cancelAndClearActiveLoadingJob()

            // Recalculate priority plan centered on targetIndex
            val remainingIndices = currentFilePaths?.indices?.filter { it != targetIndex } ?: emptyList()
            val loadPriorityPlan =
                PlaylistAsyncLoadPriorityPolicy.buildPlan(
                    remainingIndices = remainingIndices,
                    firstTrackIndex = targetIndex,
                )

            LogUtils.d(
                "AudioPlayerService",
                "Rebalancing priorities after large jump: target=$targetIndex, " +
                    "criticalPrevious=${loadPriorityPlan.criticalPrevious.size}, " +
                    "criticalNext=${loadPriorityPlan.criticalNext.size}",
            )

            // Reload tracks with new priority plan
            loadRemainingTracksWithNewPriority(
                targetIndex = targetIndex,
                loadPriorityPlan = loadPriorityPlan,
            )
        }
    }

    /**
     * Helper to reload remaining tracks with new priority plan.
     * Extracted from preparePlaybackAsync for reuse.
     */
    private suspend fun PlaylistManager.loadRemainingTracksWithNewPriority(
        targetIndex: Int,
        loadPriorityPlan: LoadPriorityPlan,
    ) {
        val filePaths = currentFilePaths ?: return
        val dataSourceFactory = SimpleMediaDataSourceFactory()
        val addedIndices = mutableSetOf<Int>(targetIndex)

        // Helper to load a single MediaItem
        suspend fun loadMediaItem(
            index: Int,
            priority: String,
        ) {
            val loadStartTime = System.currentTimeMillis()
            val filePath = filePaths[index]
            val fileName = filePath.substringAfterLast('/')
            try {
                LogUtils.d(
                    "AudioPlayerService",
                    "📥 Loading track $index: $fileName (priority: $priority) - REBALANCING",
                )
                val mediaSource =
                    createMediaSourceForIndex(
                        filePaths,
                        index,
                        currentMetadata,
                        dataSourceFactory,
                    )

                // Wait for previous indices
                val orderedInsertWaitDecision = PlaylistOrderedInsertWaitPolicy.decide()
                var waitAttempts = 0
                while (
                    PlaylistOrderedInsertWaitPolicy.shouldContinueWaiting(
                        waitAttempts = waitAttempts,
                        maxAttempts = orderedInsertWaitPolicy.maxAttempts,
                    )
                ) {
                    val allPreviousAdded =
                        withContext(dispatchers.main) {
                            addMutex.withLock {
                                PlaylistOrderedInsertWaitPolicy.areAllPreviousIndicesAdded(
                                    index = index,
                                    firstTrackIndex = targetIndex,
                                    addedIndices = addedIndices,
                                )
                            }
                        }
                    if (allPreviousAdded) {
                        break
                    }
                    kotlinx.coroutines.delay(orderedInsertWaitPolicy.delayMs)
                    waitAttempts++
                }

                // Add to player
                withContext(dispatchers.main) {
                    addMutex.withLock {
                        val activePlayer = getActivePlayer()
                        val currentCount = activePlayer.mediaItemCount

                        var existingPathAtIndex: String? = null
                        if (currentCount > index) {
                            try {
                                existingPathAtIndex =
                                    activePlayer
                                        .getMediaItemAt(index)
                                        .localConfiguration
                                        ?.uri
                                        ?.path
                            } catch (_: IndexOutOfBoundsException) {
                            }
                        }
                        val dedupDecision =
                            PlaylistAddDedupPolicy.decide(
                                index = index,
                                expectedPath = filePaths[index],
                                addedIndices = addedIndices,
                                currentPlayerItemCount = currentCount,
                                existingPathAtIndex = existingPathAtIndex,
                            )
                        if (dedupDecision.shouldMarkAdded) {
                            addedIndices.add(index)
                        }
                        if (dedupDecision.shouldSkipAdd) {
                            val reason =
                                when (dedupDecision.reason) {
                                    PlaylistAddDedupReason.ALREADY_MARKED_ADDED -> "already added"
                                    PlaylistAddDedupReason.PLAYER_ALREADY_HAS_EXPECTED_ITEM -> "already exists in player"
                                    PlaylistAddDedupReason.PROCEED -> "skip"
                                }
                            LogUtils.w(
                                "AudioPlayerService",
                                "MediaItem at index $index $reason, skipping duplicate",
                            )
                            return@withContext
                        }

                        activePlayer.addMediaSource(index, mediaSource)
                        addedIndices.add(index)
                        val loadDuration = System.currentTimeMillis() - loadStartTime
                        LogUtils.i(
                            "AudioPlayerService",
                            "✅ Loaded track $index: $fileName (${loadDuration}ms, priority: $priority, playlist size: ${activePlayer.mediaItemCount})",
                        )
                    }
                }
            } catch (e: Exception) {
                val loadDuration = System.currentTimeMillis() - loadStartTime
                LogUtils.e(
                    "AudioPlayerService",
                    "❌ Failed to load track $index: $fileName (${loadDuration}ms, priority: $priority): ${e.message}",
                    e,
                )
            }
        }

        // Load all tracks in parallel
        val allIndices = loadPriorityPlan.orderedIndices
        val jobs =
            allIndices.map { index ->
                val priority = PlaylistAsyncLoadPriorityPolicy.priorityLabelFor(index, loadPriorityPlan)
                scope.launch {
                    loadMediaItem(index, priority)
                }
            }

        // Wait for all jobs to complete
        kotlinx.coroutines.joinAll(*jobs.toTypedArray())
    }
}
