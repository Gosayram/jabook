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

package com.jabook.app.jabook.compose.data.local.scanner

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid scanner that chooses optimal scanning strategy.
 *
 * Strategy:
 * - If custom paths configured → Use DirectFileSystemScanner (ignores .nomedia)
 * - If no custom paths → Use MediaStoreBookScanner (fast, uses index)
 *
 * This allows:
 * - Fast scanning for users with default folders
 * - .nomedia support for users with custom paths (e.g., hiding images from gallery)
 */
@Singleton
public class HybridBookScanner
    @Inject
    constructor(
        private val mediaStoreScanner: MediaStoreBookScanner,
        private val directScanner: DirectFileSystemScanner,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val loggerFactory: LoggerFactory,
    ) : LocalBookScanner {
        private val logger = loggerFactory.get("HybridBookScanner")

        // Merge progress from both scanners (one is active at a time)
        override val scanProgress: kotlinx.coroutines.flow.StateFlow<ScanProgress> =
            merge(mediaStoreScanner.scanProgress, directScanner.scanProgress)
                .stateIn(
                    scope = CoroutineScope(Dispatchers.Default),
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ScanProgress.Idle,
                )

        override suspend fun scanAudiobooks(): Result<List<ScannedBook>, com.jabook.app.jabook.compose.domain.model.AppError> {
            // CRITICAL FIX: Validate and clean up non-existent folders before scanning
            // Remove folders that were deleted from filesystem
            val customPaths = scanPathDao.getAllPathsList()
            var removedCount = 0
            for (pathEntity in customPaths) {
                val folder = java.io.File(pathEntity.path)
                if (!folder.exists() || !folder.isDirectory) {
                    logger.w { "Removing non-existent scan folder: ${pathEntity.path}" }
                    scanPathDao.deletePath(pathEntity)
                    removedCount++
                }
            }

            if (removedCount > 0) {
                logger.i { "Cleaned up $removedCount deleted scan folders" }
            }

            // Get updated list after cleanup
            val validPaths = scanPathDao.getAllPathsList()

            return if (validPaths.isEmpty()) {
                // No custom paths - use MediaStore (fast, indexed)
                logger.d { "Using MediaStore scanner (no custom paths)" }
                mediaStoreScanner.scanAudiobooks()
            } else {
                // Has custom paths - use direct file system scan
                // This ignores .nomedia files (user's use case: hide images, show audio)
                logger.d { "Using direct file scanner (${validPaths.size} custom paths)" }
                directScanner.scanAudiobooks()
            }
        }
    }
