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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StorageTransferWorkflowTest {
    private val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

    @Test
    fun `transferFile copies source to target and verifies integrity`() {
        val source = createTempFileWithContent("source-audio-content")
        val targetDir = Files.createTempDirectory("jabook-target").toFile()
        val target = File(targetDir, "book.mp3")
        val workflow = StorageTransferWorkflow(preflightChecker = checker)

        val result =
            workflow.transferFile(
                sourcePath = source.absolutePath,
                targetPath = target.absolutePath,
                overwrite = false,
            )

        assertTrue(result.isSuccess)
        assertEquals(StorageTransferWorkflowFailureReason.NONE, result.failureReason)
        assertTrue(target.exists())
        assertEquals("source-audio-content", target.readText())

        source.delete()
        target.delete()
        targetDir.deleteRecursively()
    }

    @Test
    fun `transferFile restores original target when integrity check fails`() {
        val source = createTempFileWithContent("fresh-data")
        val targetDir = Files.createTempDirectory("jabook-target").toFile()
        val target = File(targetDir, "book.mp3").apply { writeText("old-data") }
        val workflow =
            StorageTransferWorkflow(
                preflightChecker = checker,
                postCopyHook = { tempFile ->
                    tempFile.writeText("corrupted-data")
                },
            )

        val result =
            workflow.transferFile(
                sourcePath = source.absolutePath,
                targetPath = target.absolutePath,
                overwrite = true,
            )

        assertFalse(result.isSuccess)
        assertEquals(
            StorageTransferWorkflowFailureReason.INTEGRITY_CHECK_FAILED,
            result.failureReason,
        )
        assertTrue(result.rollbackPerformed)
        assertEquals("old-data", target.readText())

        source.delete()
        target.delete()
        targetDir.deleteRecursively()
    }

    @Test
    fun `transferFile fails when target exists and overwrite disabled`() {
        val source = createTempFileWithContent("new-data")
        val targetDir = Files.createTempDirectory("jabook-target").toFile()
        val target = File(targetDir, "book.mp3").apply { writeText("existing") }
        val workflow = StorageTransferWorkflow(preflightChecker = checker)

        val result =
            workflow.transferFile(
                sourcePath = source.absolutePath,
                targetPath = target.absolutePath,
                overwrite = false,
            )

        assertFalse(result.isSuccess)
        assertEquals(
            StorageTransferWorkflowFailureReason.TARGET_ALREADY_EXISTS,
            result.failureReason,
        )
        assertEquals("existing", target.readText())

        source.delete()
        target.delete()
        targetDir.deleteRecursively()
    }

    @Test
    fun `transferFile rethrows cancellation exception`() {
        val source = createTempFileWithContent("source-audio-content")
        val targetDir = Files.createTempDirectory("jabook-target").toFile()
        val target = File(targetDir, "book.mp3")
        val workflow =
            StorageTransferWorkflow(
                preflightChecker = checker,
                postCopyHook = {
                    throw CancellationException("cancel transfer")
                },
            )

        try {
            workflow.transferFile(
                sourcePath = source.absolutePath,
                targetPath = target.absolutePath,
                overwrite = false,
            )
            fail("Expected cancellation to be rethrown")
        } catch (actual: CancellationException) {
            assertEquals("cancel transfer", actual.message)
        }

        source.delete()
        target.delete()
        targetDir.deleteRecursively()
    }

    @Test
    fun `transferFile rejects traversal segments in paths`() {
        val sourceDir = Files.createTempDirectory("jabook-source").toFile()
        val source = File(sourceDir, "book.mp3").apply { writeText("audio") }
        val targetDir = Files.createTempDirectory("jabook-target").toFile()
        val traversalTargetPath = "${targetDir.absolutePath}/../escape.mp3"
        val workflow = StorageTransferWorkflow(preflightChecker = checker)

        val result =
            workflow.transferFile(
                sourcePath = source.absolutePath,
                targetPath = traversalTargetPath,
                overwrite = false,
            )

        assertFalse(result.isSuccess)
        assertEquals(
            StorageTransferWorkflowFailureReason.TARGET_PATH_VALIDATION_FAILED,
            result.failureReason,
        )
        assertFalse(File(targetDir.parentFile, "escape.mp3").exists())

        source.delete()
        sourceDir.deleteRecursively()
        targetDir.deleteRecursively()
    }

    @Test
    fun `move policy retries without atomic move when a removable filesystem does not support it`() {
        val attemptedOptions = mutableListOf<Set<CopyOption>>()

        StorageTransferMovePolicy.moveTempIntoPlace(
            source = File("temp"),
            target = File("target"),
        ) { options ->
            attemptedOptions += options.toSet()
            if (StandardCopyOption.ATOMIC_MOVE in options) {
                throw AtomicMoveNotSupportedException("temp", "target", "unsupported")
            }
        }

        assertEquals(
            listOf(
                setOf(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE),
                setOf(StandardCopyOption.REPLACE_EXISTING),
            ),
            attemptedOptions,
        )
    }

    @Test
    fun `concurrent non-overwriting transfers preserve first completed target`() {
        val firstSource = createTempFileWithContent("first")
        val secondSource = createTempFileWithContent("second")
        val targetDir = Files.createTempDirectory("jabook-target").toFile()
        val target = File(targetDir, "book.mp3")
        val firstCopyStarted = CountDownLatch(1)
        val allowFirstTransfer = CountDownLatch(1)
        val firstWorkflow =
            StorageTransferWorkflow(
                preflightChecker = checker,
                postCopyHook = {
                    firstCopyStarted.countDown()
                    check(allowFirstTransfer.await(5, TimeUnit.SECONDS))
                },
            )
        val secondWorkflow = StorageTransferWorkflow(preflightChecker = checker)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first =
                executor.submit<StorageTransferWorkflowResult> {
                    firstWorkflow.transferFile(firstSource.absolutePath, target.absolutePath, overwrite = false)
                }
            assertTrue(firstCopyStarted.await(5, TimeUnit.SECONDS))

            val second =
                executor.submit<StorageTransferWorkflowResult> {
                    secondWorkflow.transferFile(secondSource.absolutePath, target.absolutePath, overwrite = false)
                }
            allowFirstTransfer.countDown()

            assertTrue(first.get(5, TimeUnit.SECONDS).isSuccess)
            assertEquals(
                StorageTransferWorkflowFailureReason.TARGET_ALREADY_EXISTS,
                second.get(5, TimeUnit.SECONDS).failureReason,
            )
            assertEquals("first", target.readText())
        } finally {
            allowFirstTransfer.countDown()
            executor.shutdownNow()
            firstSource.delete()
            secondSource.delete()
            targetDir.deleteRecursively()
        }
    }

    private fun createTempFileWithContent(content: String): File {
        val file = Files.createTempFile("jabook-transfer-workflow", ".tmp").toFile()
        file.writeText(content)
        return file
    }
}
