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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Policy for incremental scanning: determines which files and directories
 * need re-scanning based on their `lastModified` timestamp compared to the
 * last successful scan timestamp for each scan path.
 *
 * Design principles:
 * - Files unchanged since last scan are skipped entirely (no metadata re-parsing).
 * - Directories containing at least one changed file are included in the scan.
 * - If no previous scan timestamp exists, all files are included (full scan fallback).
 * - A configurable grace window prevents skipping files whose timestamps are
 *   within a small margin of the last scan (handles filesystem time granularity).
 */
@Singleton
public class IncrementalScanPolicy
    @Inject
    constructor() {
        /**
         * Result of an incremental scan filter pass.
         *
         * @property filesToScan Files that need re-scanning (new or modified since last scan).
         * @property skippedCount Number of files skipped because they haven't changed.
         * @property isFullScan True if no previous scan timestamp was available (full scan).
         */
        public data class FilterResult(
            val filesToScan: List<FileScanInfo>,
            val skippedCount: Int,
            val isFullScan: Boolean,
        )

        /**
         * Lightweight info about a file relevant for incremental scan decisions.
         *
         * @property filePath Absolute path to the file.
         * @property displayName File name (without directory).
         * @property directory Parent directory path.
         * @property size File size in bytes.
         * @property lastModified File last-modified timestamp in millis.
         */
        public data class FileScanInfo(
            val filePath: String,
            val displayName: String,
            val directory: String,
            val size: Long,
            val lastModified: Long,
        )

        /**
         * Filters files to only those that have changed since the last scan.
         *
         * @param allFiles All audio files found during the fast discovery phase.
         * @param lastScanTimestampMs The timestamp of the last successful scan for this path,
         *   or `null` if no previous scan exists (triggers full scan).
         * @param graceWindowMs Grace window in milliseconds to account for filesystem
         *   timestamp granularity. Files modified within this window before the scan
         *   timestamp are still included. Defaults to [DEFAULT_GRACE_WINDOW_MS].
         * @return [FilterResult] containing only files that need re-scanning.
         */
        public fun filterChangedFiles(
            allFiles: List<FileScanInfo>,
            lastScanTimestampMs: Long?,
            graceWindowMs: Long = DEFAULT_GRACE_WINDOW_MS,
        ): FilterResult {
            // No previous scan timestamp -> full scan (all files included)
            if (lastScanTimestampMs == null || lastScanTimestampMs <= 0L) {
                return FilterResult(
                    filesToScan = allFiles,
                    skippedCount = 0,
                    isFullScan = true,
                )
            }

            // Effective cutoff: files modified at or after (lastScan - graceWindow) are considered changed
            val effectiveCutoff = lastScanTimestampMs - graceWindowMs

            val changed = mutableListOf<FileScanInfo>()
            var skipped = 0

            for (file in allFiles) {
                if (file.lastModified >= effectiveCutoff) {
                    changed.add(file)
                } else {
                    skipped++
                }
            }

            return FilterResult(
                filesToScan = changed,
                skippedCount = skipped,
                isFullScan = false,
            )
        }

        /**
         * Groups files by directory, but only includes directories that have
         * at least one changed file. This avoids re-processing entirely
         * unchanged directories.
         *
         * @param filteredResult The result from [filterChangedFiles].
         * @return Map of directory path to list of files in that directory that need scanning.
         */
        public fun groupChangedDirectories(filteredResult: FilterResult): Map<String, List<FileScanInfo>> =
            filteredResult.filesToScan.groupBy { it.directory }

        public companion object {
            /**
             * Default grace window: 2 seconds.
             *
             * Accounts for filesystem timestamp granularity where some filesystems
             * (e.g., FAT32) have 2-second resolution.
             */
            public const val DEFAULT_GRACE_WINDOW_MS: Long = 2000L
        }
    }
