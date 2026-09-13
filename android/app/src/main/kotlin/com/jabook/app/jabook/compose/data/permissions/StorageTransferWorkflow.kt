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

import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

public enum class StorageTransferWorkflowFailureReason {
    NONE,
    SOURCE_PRECHECK_FAILED,
    TARGET_PRECHECK_FAILED,
    TARGET_PATH_VALIDATION_FAILED,
    TARGET_ALREADY_EXISTS,
    COPY_FAILED,
    INTEGRITY_CHECK_FAILED,
}

public data class StorageTransferWorkflowResult(
    val isSuccess: Boolean,
    val targetPath: String,
    val failureReason: StorageTransferWorkflowFailureReason = StorageTransferWorkflowFailureReason.NONE,
    val preflightFailureReason: StorageTransferPreflightFailureReason? = null,
    val rollbackPerformed: Boolean = false,
)

/**
 * Safe file transfer workflow for storage migration paths:
 * 1) preflight source/target directories
 * 2) copy to temp target
 * 3) atomically replace target
 * 4) verify checksum
 * 5) rollback target when integrity check fails
 */
public class StorageTransferWorkflow(
    private val preflightChecker: ExternalStoragePreflightChecker,
    private val postCopyHook: ((tempTarget: File) -> Unit)? = null,
) {
    public fun transferFile(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): StorageTransferWorkflowResult =
        StorageTransferPathLocks.withLock(targetPath) {
            transferFileLocked(
                sourcePath = sourcePath,
                targetPath = targetPath,
                overwrite = overwrite,
            )
        }

    private fun transferFileLocked(
        sourcePath: String,
        targetPath: String,
        overwrite: Boolean,
    ): StorageTransferWorkflowResult {
        if (containsTraversalSegment(sourcePath) || containsTraversalSegment(targetPath)) {
            return StorageTransferWorkflowResult(
                isSuccess = false,
                targetPath = targetPath,
                failureReason = StorageTransferWorkflowFailureReason.TARGET_PATH_VALIDATION_FAILED,
            )
        }

        val sourceFile = File(sourcePath)
        val targetFile = File(targetPath)
        val initialCanonicalTargetDir =
            runCatching { targetFile.parentFile?.canonicalPath }
                .getOrNull()
                ?: return StorageTransferWorkflowResult(
                    isSuccess = false,
                    targetPath = targetPath,
                    failureReason = StorageTransferWorkflowFailureReason.TARGET_PATH_VALIDATION_FAILED,
                )

        val sourceDirResult = preflightChecker.checkDirectory(sourceFile.parent)
        if (!sourceDirResult.isSuccess) {
            return StorageTransferWorkflowResult(
                isSuccess = false,
                targetPath = targetPath,
                failureReason = StorageTransferWorkflowFailureReason.SOURCE_PRECHECK_FAILED,
            )
        }

        val targetDirResult = preflightChecker.checkDirectory(targetFile.parent)
        if (!targetDirResult.isSuccess) {
            return StorageTransferWorkflowResult(
                isSuccess = false,
                targetPath = targetPath,
                failureReason = StorageTransferWorkflowFailureReason.TARGET_PRECHECK_FAILED,
            )
        }

        if (targetFile.exists() && !overwrite) {
            return StorageTransferWorkflowResult(
                isSuccess = false,
                targetPath = targetPath,
                failureReason = StorageTransferWorkflowFailureReason.TARGET_ALREADY_EXISTS,
            )
        }

        val tempTarget = File(targetFile.parentFile, "${targetFile.name}.tmp.${UUID.randomUUID()}")
        val backupTarget = File(targetFile.parentFile, "${targetFile.name}.bak.${UUID.randomUUID()}")
        var rollbackPerformed = false
        return try {
            Files.copy(
                sourceFile.toPath(),
                tempTarget.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES,
            )
            postCopyHook?.invoke(tempTarget)

            if (overwrite && targetFile.exists()) {
                Files.move(
                    targetFile.toPath(),
                    backupTarget.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            if (!isTargetPathStillSafe(targetFile, initialCanonicalTargetDir)) {
                rollbackPerformed = true
                restoreBackupOrDeleteTarget(targetFile = targetFile, backupTarget = backupTarget)
                return StorageTransferWorkflowResult(
                    isSuccess = false,
                    targetPath = targetPath,
                    failureReason = StorageTransferWorkflowFailureReason.TARGET_PATH_VALIDATION_FAILED,
                    rollbackPerformed = true,
                )
            }

            StorageTransferMovePolicy.moveTempIntoPlace(
                source = tempTarget,
                target = targetFile,
            )

            val integrity =
                preflightChecker.verifyTransferIntegrity(
                    sourcePath = sourcePath,
                    targetPath = targetPath,
                )
            if (!integrity.isSuccess) {
                rollbackPerformed = true
                restoreBackupOrDeleteTarget(targetFile = targetFile, backupTarget = backupTarget)
                return StorageTransferWorkflowResult(
                    isSuccess = false,
                    targetPath = targetPath,
                    failureReason = StorageTransferWorkflowFailureReason.INTEGRITY_CHECK_FAILED,
                    preflightFailureReason = integrity.failureReason,
                    rollbackPerformed = true,
                )
            }

            backupTarget.delete()
            StorageTransferWorkflowResult(
                isSuccess = true,
                targetPath = targetPath,
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            rollbackPerformed = true
            restoreBackupOrDeleteTarget(targetFile = targetFile, backupTarget = backupTarget)
            StorageTransferWorkflowResult(
                isSuccess = false,
                targetPath = targetPath,
                failureReason = StorageTransferWorkflowFailureReason.COPY_FAILED,
                rollbackPerformed = true,
            )
        } finally {
            if (tempTarget.exists()) {
                tempTarget.delete()
            }
            if (!rollbackPerformed && backupTarget.exists()) {
                backupTarget.delete()
            }
        }
    }

    private fun restoreBackupOrDeleteTarget(
        targetFile: File,
        backupTarget: File,
    ) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (backupTarget.exists()) {
            Files.move(
                backupTarget.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun isTargetPathStillSafe(
        targetFile: File,
        initialCanonicalTargetDir: String,
    ): Boolean {
        val currentCanonicalTargetDir =
            runCatching { targetFile.parentFile?.canonicalPath }
                .getOrNull()
                ?: return false
        if (currentCanonicalTargetDir != initialCanonicalTargetDir) return false

        val canonicalTargetPath =
            runCatching { targetFile.canonicalPath }
                .getOrNull()
                ?: return false

        return canonicalTargetPath == initialCanonicalTargetDir ||
            canonicalTargetPath.startsWith("$initialCanonicalTargetDir${File.separator}")
    }

    private fun containsTraversalSegment(path: String): Boolean = path.replace('\\', '/').split('/').any { it == ".." }
}

/**
 * Moves a fully written temporary file into place. FAT/exFAT volumes do not
 * support atomic renames, but the previous target has already been moved to a
 * backup, so a replace-only move retains rollback safety there.
 */
internal object StorageTransferMovePolicy {
    fun moveTempIntoPlace(
        source: File,
        target: File,
        move: (Array<CopyOption>) -> Unit = { options -> Files.move(source.toPath(), target.toPath(), *options) },
    ) {
        try {
            move(arrayOf(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE))
        } catch (_: AtomicMoveNotSupportedException) {
            move(arrayOf(StandardCopyOption.REPLACE_EXISTING))
        }
    }
}

/**
 * Serializes transfers that target the same canonical path within this process.
 *
 * This closes the check-then-move race between concurrent migration requests while
 * retaining a bounded lock set for long-running app processes.
 */
private object StorageTransferPathLocks {
    private const val LOCK_STRIPES: Int = 64
    private val locks: Array<Any> = Array(LOCK_STRIPES) { Any() }

    fun <T> withLock(
        targetPath: String,
        block: () -> T,
    ): T {
        val normalizedPath = runCatching { File(targetPath).canonicalPath }.getOrElse { targetPath }
        val lock = locks[Math.floorMod(normalizedPath.hashCode(), LOCK_STRIPES)]
        return synchronized(lock, block)
    }
}
