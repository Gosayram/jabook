/*
 * Copyright (c) 2025 JaBook authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jabook.app.jabook.compose.data.permissions

import android.os.StatFs
import java.io.File

/**
 * Result of a storage health check.
 *
 * @property availableBytes available bytes on the storage volume, or null if check failed.
 * @property totalBytes total bytes on the storage volume, or null if check failed.
 * @property isHealthy true if available space is above the configured threshold.
 * @property warningMessage human-readable warning if storage is low, null if healthy.
 */
public data class StorageHealthResult(
    val availableBytes: Long?,
    val totalBytes: Long?,
    val isHealthy: Boolean,
    val warningMessage: String?,
)

/**
 * Checks storage health before critical operations (migration, transfer, backup export).
 *
 * Uses [StatFs] to query available space and compares against a configurable threshold.
 * Designed for use in preflight checks and startup diagnostics.
 *
 * @property minAvailableBytes minimum required free bytes (default 50 MB).
 */
public class StorageHealthChecker(
    private val minAvailableBytes: Long = DEFAULT_MIN_AVAILABLE_BYTES,
) {
    /**
     * Checks storage health for the volume containing [path].
     *
     * @param path file path on the target storage volume.
     * @return [StorageHealthResult] with available/total bytes and health status.
     */
    public fun check(path: String): StorageHealthResult =
        try {
            val file = File(path)
            val stat = StatFs(file.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val isHealthy = availableBytes >= minAvailableBytes
            StorageHealthResult(
                availableBytes = availableBytes,
                totalBytes = totalBytes,
                isHealthy = isHealthy,
                warningMessage =
                    if (!isHealthy) {
                        "Low storage: ${formatBytes(availableBytes)} available " +
                            "(minimum ${formatBytes(minAvailableBytes)} required)"
                    } else {
                        null
                    },
            )
        } catch (e: Exception) {
            StorageHealthResult(
                availableBytes = null,
                totalBytes = null,
                isHealthy = false,
                warningMessage = "Cannot check storage: ${e.message}",
            )
        }

    /**
     * Checks storage health for the volume containing [file].
     */
    public fun check(file: File): StorageHealthResult = check(file.absolutePath)

    public companion object {
        /** Default minimum available bytes: 50 MB. */
        public const val DEFAULT_MIN_AVAILABLE_BYTES: Long = 50L * 1024 * 1024

        /**
         * Formats byte count as human-readable string (e.g., "50 MB", "1.5 GB").
         */
        public fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.1f GB".format(gb)
        }
    }
}
