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

package com.jabook.app.jabook.compose.data.permissions

import java.io.File
import java.security.MessageDigest

public enum class StoragePathPreflightFailureReason {
    NONE,
    INVALID_PATH,
    STORAGE_PERMISSION_MISSING,
    PATH_NOT_FOUND,
    NOT_A_DIRECTORY,
    NOT_READABLE,
    NOT_WRITABLE,
}

public data class StoragePathPreflightResult(
    val isSuccess: Boolean,
    val normalizedPath: String? = null,
    val isLikelyUsbOrOtg: Boolean = false,
    val failureReason: StoragePathPreflightFailureReason = StoragePathPreflightFailureReason.NONE,
)

public enum class StorageTransferPreflightFailureReason {
    NONE,
    STORAGE_PERMISSION_MISSING,
    SOURCE_MISSING,
    SOURCE_NOT_FILE,
    TARGET_MISSING,
    TARGET_NOT_FILE,
    CHECKSUM_MISMATCH,
}

public data class StorageTransferPreflightResult(
    val isSuccess: Boolean,
    val sourcePath: String? = null,
    val targetPath: String? = null,
    val failureReason: StorageTransferPreflightFailureReason = StorageTransferPreflightFailureReason.NONE,
)

/**
 * Performs preflight checks for full-storage workflows (including USB/OTG paths)
 * before migration/copy operations are attempted.
 */
public class ExternalStoragePreflightChecker(
    private val hasFullStoragePermission: () -> Boolean,
) {
    public fun checkDirectory(path: String?): StoragePathPreflightResult {
        val rawPath = path?.trim().orEmpty()
        if (rawPath.isEmpty()) {
            return StoragePathPreflightResult(
                isSuccess = false,
                failureReason = StoragePathPreflightFailureReason.INVALID_PATH,
            )
        }

        if (!hasFullStoragePermission()) {
            return StoragePathPreflightResult(
                isSuccess = false,
                normalizedPath = rawPath,
                isLikelyUsbOrOtg = isLikelyUsbOrOtgPath(rawPath),
                failureReason = StoragePathPreflightFailureReason.STORAGE_PERMISSION_MISSING,
            )
        }

        val file = File(rawPath)
        if (!file.exists()) {
            return StoragePathPreflightResult(
                isSuccess = false,
                normalizedPath = rawPath,
                isLikelyUsbOrOtg = isLikelyUsbOrOtgPath(rawPath),
                failureReason = StoragePathPreflightFailureReason.PATH_NOT_FOUND,
            )
        }

        if (!file.isDirectory) {
            return StoragePathPreflightResult(
                isSuccess = false,
                normalizedPath = file.absolutePath,
                isLikelyUsbOrOtg = isLikelyUsbOrOtgPath(file.absolutePath),
                failureReason = StoragePathPreflightFailureReason.NOT_A_DIRECTORY,
            )
        }

        val canonicalPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        val isUsbOrOtgPath = isLikelyUsbOrOtgPath(canonicalPath)
        if (!file.canRead()) {
            return StoragePathPreflightResult(
                isSuccess = false,
                normalizedPath = canonicalPath,
                isLikelyUsbOrOtg = isUsbOrOtgPath,
                failureReason = StoragePathPreflightFailureReason.NOT_READABLE,
            )
        }

        if (!file.canWrite()) {
            return StoragePathPreflightResult(
                isSuccess = false,
                normalizedPath = canonicalPath,
                isLikelyUsbOrOtg = isUsbOrOtgPath,
                failureReason = StoragePathPreflightFailureReason.NOT_WRITABLE,
            )
        }

        return StoragePathPreflightResult(
            isSuccess = true,
            normalizedPath = canonicalPath,
            isLikelyUsbOrOtg = isUsbOrOtgPath,
        )
    }

    public fun verifyTransferIntegrity(
        sourcePath: String,
        targetPath: String,
    ): StorageTransferPreflightResult {
        if (!hasFullStoragePermission()) {
            return StorageTransferPreflightResult(
                isSuccess = false,
                sourcePath = sourcePath,
                targetPath = targetPath,
                failureReason = StorageTransferPreflightFailureReason.STORAGE_PERMISSION_MISSING,
            )
        }

        val source = File(sourcePath)
        val target = File(targetPath)
        if (!source.exists()) {
            return StorageTransferPreflightResult(
                isSuccess = false,
                sourcePath = sourcePath,
                targetPath = targetPath,
                failureReason = StorageTransferPreflightFailureReason.SOURCE_MISSING,
            )
        }
        if (!source.isFile) {
            return StorageTransferPreflightResult(
                isSuccess = false,
                sourcePath = sourcePath,
                targetPath = targetPath,
                failureReason = StorageTransferPreflightFailureReason.SOURCE_NOT_FILE,
            )
        }
        if (!target.exists()) {
            return StorageTransferPreflightResult(
                isSuccess = false,
                sourcePath = sourcePath,
                targetPath = targetPath,
                failureReason = StorageTransferPreflightFailureReason.TARGET_MISSING,
            )
        }
        if (!target.isFile) {
            return StorageTransferPreflightResult(
                isSuccess = false,
                sourcePath = sourcePath,
                targetPath = targetPath,
                failureReason = StorageTransferPreflightFailureReason.TARGET_NOT_FILE,
            )
        }

        val sourceChecksum = sha256(source)
        val targetChecksum = sha256(target)
        if (sourceChecksum != targetChecksum) {
            return StorageTransferPreflightResult(
                isSuccess = false,
                sourcePath = sourcePath,
                targetPath = targetPath,
                failureReason = StorageTransferPreflightFailureReason.CHECKSUM_MISMATCH,
            )
        }

        return StorageTransferPreflightResult(
            isSuccess = true,
            sourcePath = sourcePath,
            targetPath = targetPath,
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun isLikelyUsbOrOtgPath(path: String): Boolean {
        val normalized = path.lowercase()
        return normalized.startsWith("/storage/") &&
            !normalized.startsWith("/storage/emulated") ||
            normalized.contains("/mnt/media_rw/") ||
            normalized.contains("/usb") ||
            normalized.contains("/otg")
    }
}
