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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
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

    private fun createTempFileWithContent(content: String): File {
        val file = Files.createTempFile("jabook-transfer-workflow", ".tmp").toFile()
        file.writeText(content)
        return file
    }
}
