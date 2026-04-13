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
package com.jabook.app.jabook.compose.data.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption

/**
 * Result of an atomic file write operation.
 *
 * @property targetFile the final target file after successful rename.
 * @property bytesWritten number of bytes written.
 */
public data class AtomicWriteResult(
    val targetFile: File,
    val bytesWritten: Long,
)

/**
 * Performs atomic file writes using the write-to-temp-then-rename pattern.
 *
 * On Linux/Android, `rename()` on the same filesystem is atomic, ensuring that
 * readers never see a partially written file. This is critical for:
 * - Backup export
 * - Cover cache writes
 * - Migration output files
 * - Position persistence snapshots
 *
 * Usage:
 * ```
 * val result = AtomicFileWriter.writeAtomically(targetFile) { tempStream ->
 *     tempStream.write(data)
 *     data.size.toLong()
 * }
 * ```
 *
 * Thread-safety: For cross-process safety, use [writeWithLock] which combines
 * file locking with atomic write.
 */
public object AtomicFileWriter {
    /**
     * Writes data to [targetFile] atomically via temp file + rename.
     *
     * The write flow:
     * 1. Write to `target.tmp` in the same directory
     * 2. Sync to disk (`fsync`)
     * 3. Rename `target.tmp` → `targetFile` (atomic on same filesystem)
     * 4. Sync parent directory for rename durability
     *
     * @param targetFile the final destination file.
     * @param overwrite if false and target exists, throws [IOException].
     * @param block writer lambda that receives the temp [FileOutputStream].
     *     Must return the number of bytes written.
     * @return [AtomicWriteResult] with target file and bytes written.
     * @throws IOException if write, sync, or rename fails.
     */
    public fun writeAtomically(
        targetFile: File,
        overwrite: Boolean = true,
        block: (FileOutputStream) -> Long,
    ): AtomicWriteResult {
        val parentDir = targetFile.parentFile
        require(parentDir != null && (parentDir.exists() || parentDir.mkdirs())) {
            "Cannot access parent directory for ${targetFile.absolutePath}"
        }

        if (!overwrite && targetFile.exists()) {
            throw IOException("Target file already exists: ${targetFile.absolutePath}")
        }

        val tempFile = File(parentDir, "${targetFile.name}.tmp")

        try {
            // Step 1: Write to temp file
            val bytesWritten =
                FileOutputStream(tempFile).use { stream ->
                    val written = block(stream)
                    // Step 2: Sync to disk before rename
                    stream.fd.sync()
                    written
                }

            // Step 3: Atomic rename (same filesystem guarantee)
            if (!tempFile.renameTo(targetFile)) {
                throw IOException(
                    "Atomic rename failed: ${tempFile.absolutePath} -> ${targetFile.absolutePath}",
                )
            }

            // Step 4: Sync parent directory for rename durability
            syncParentDirectory(parentDir)

            return AtomicWriteResult(
                targetFile = targetFile,
                bytesWritten = bytesWritten,
            )
        } catch (e: Exception) {
            // Cleanup temp file on any failure
            tempFile.delete()
            if (e is IOException) throw e
            throw IOException("Atomic write failed for ${targetFile.absolutePath}", e)
        }
    }

    /**
     * Writes data atomically with an exclusive file lock for cross-process safety.
     *
     * Uses [FileChannel.tryLock] to prevent concurrent writes from multiple
     * processes or threads. Combines with the atomic temp-file pattern for
     * maximum safety.
     *
     * @param targetFile the final destination file.
     * @param block writer lambda that receives the locked [FileOutputStream].
     *     Must return the number of bytes written.
     * @return [AtomicWriteResult] with target file and bytes written.
     * @throws IOException if lock cannot be acquired or write fails.
     */
    public fun writeWithLock(
        targetFile: File,
        block: (FileOutputStream) -> Long,
    ): AtomicWriteResult {
        val lockFile = File(targetFile.parentFile, "${targetFile.name}.lock")

        return try {
            val channel =
                java.nio.file.Files.newByteChannel(
                    lockFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )

            channel.use { ch ->
                val lock: FileLock =
                    (ch as FileChannel).tryLock()
                        ?: throw IOException("Cannot acquire file lock: ${lockFile.absolutePath}")

                lock.use {
                    writeAtomically(targetFile, overwrite = true, block)
                }
            }
        } finally {
            // Clean up lock file (best effort)
            lockFile.delete()
        }
    }

    /**
     * Attempts to acquire an exclusive lock on the given file.
     *
     * Useful for protecting critical sections beyond just writes (e.g., migration
     * operations that span multiple files).
     *
     * @param lockFile the lock file to use.
     * @return [FileLock] that must be released by the caller, or null if lock
     *     cannot be acquired.
     */
    public fun tryAcquireLock(lockFile: File): FileLock? =
        try {
            val channel =
                java.nio.file.Files.newByteChannel(
                    lockFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            (channel as FileChannel).tryLock()
        } catch (_: Exception) {
            null
        }

    private fun syncParentDirectory(dir: File) {
        try {
            FileOutputStream(dir).use { stream ->
                stream.fd.sync()
            }
        } catch (_: Exception) {
            // Directory sync is best-effort on some filesystems
            // (e.g., some SD cards / USB OTG don't support it)
        }
    }
}
