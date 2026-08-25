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
import com.jabook.app.jabook.compose.core.util.PerfTrace
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
        private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
        override val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

        override suspend fun scanAudiobooks(): Result<List<ScannedBook>, com.jabook.app.jabook.compose.domain.model.AppError> =
            PerfTrace.section(name = "HybridBookScanner.scanAudiobooks") {
                // CRITICAL FIX: Validate and clean up non-existent folders before scanning
                // Remove folders that were deleted from filesystem
                val customPaths =
                    PerfTrace.section(name = "HybridBookScanner.loadPaths") {
                        scanPathDao.getAllPathsList()
                    }
                var removedCount = 0
                PerfTrace.section(name = "HybridBookScanner.cleanupInvalidPaths") {
                    for (pathEntity in customPaths) {
                        // Only delete paths we can actually verify as filesystem folders.
                        // SAF tree URIs (content://) can't be validated via File.exists() —
                        // deleting them would silently drop valid non-primary-volume scans.
                        if (pathEntity.path.startsWith("content:")) {
                            logger.w {
                                "Keeping non-filesystem scan path (unsupported storage): ${pathEntity.path}"
                            }
                            continue
                        }
                        val folder = java.io.File(pathEntity.path)
                        if (folder.exists() && !folder.isDirectory) {
                            logger.w { "Removing invalid scan folder (not a directory): ${pathEntity.path}" }
                            scanPathDao.deletePath(pathEntity)
                            removedCount++
                        } else {
                            // Missing folder may be a temporarily unmounted volume (SD hiccup).
                            // Keep the path and retry next scan instead of dropping user config.
                            logger.w { "Keeping missing scan folder (may be temporarily unmounted): ${pathEntity.path}" }
                        }
                    }
                }

                if (removedCount > 0) {
                    logger.i { "Cleaned up $removedCount deleted scan folders" }
                }

                // Get updated list after cleanup
                val validPaths =
                    PerfTrace.section(name = "HybridBookScanner.loadValidPaths") {
                        scanPathDao.getAllPathsList()
                    }

                val activeScanner =
                    if (validPaths.isEmpty()) {
                        // No custom paths - use MediaStore (fast, indexed)
                        logger.d { "Using MediaStore scanner (no custom paths)" }
                        mediaStoreScanner
                    } else {
                        // Has custom paths - use direct file system scan
                        // This ignores .nomedia files (user's use case: hide images, show audio)
                        logger.d { "Using direct file scanner (${validPaths.size} custom paths)" }
                        directScanner
                    }

                // Forward progress from active scanner without unscoped coroutine
                supervisorScope {
                    val progressJob =
                        launch {
                            activeScanner.scanProgress.collect { _scanProgress.value = it }
                        }
                    try {
                        val result =
                            PerfTrace.section(name = "HybridBookScanner.activeScan") {
                                activeScanner.scanAudiobooks()
                            }
                        result
                    } finally {
                        progressJob.cancel()
                    }
                }
            }
    }
