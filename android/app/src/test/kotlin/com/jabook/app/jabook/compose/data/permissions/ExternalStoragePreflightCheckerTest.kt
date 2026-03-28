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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class ExternalStoragePreflightCheckerTest {
    @Test
    fun `checkDirectory fails for blank path`() {
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

        val result = checker.checkDirectory("   ")

        assertFalse(result.isSuccess)
        assertEquals(StoragePathPreflightFailureReason.INVALID_PATH, result.failureReason)
    }

    @Test
    fun `checkDirectory fails when full storage permission missing`() {
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { false })

        val result = checker.checkDirectory("/storage/1234-5678/Books")

        assertFalse(result.isSuccess)
        assertEquals(StoragePathPreflightFailureReason.STORAGE_PERMISSION_MISSING, result.failureReason)
        assertTrue(result.isLikelyUsbOrOtg)
    }

    @Test
    fun `checkDirectory succeeds for existing readable writable directory`() {
        val tempDir = Files.createTempDirectory("jabook-preflight-dir").toFile()
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

        val result = checker.checkDirectory(tempDir.absolutePath)

        assertTrue(result.isSuccess)
        assertEquals(StoragePathPreflightFailureReason.NONE, result.failureReason)
        tempDir.deleteRecursively()
    }

    @Test
    fun `checkDirectory fails for non-directory path`() {
        val tempFile = Files.createTempFile("jabook-preflight-file", ".tmp").toFile()
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

        val result = checker.checkDirectory(tempFile.absolutePath)

        assertFalse(result.isSuccess)
        assertEquals(StoragePathPreflightFailureReason.NOT_A_DIRECTORY, result.failureReason)
        tempFile.delete()
    }

    @Test
    fun `verifyTransferIntegrity succeeds for matching files`() {
        val source = createTempFileWithContent("chapter-one")
        val target = createTempFileWithContent("chapter-one")
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

        val result = checker.verifyTransferIntegrity(source.absolutePath, target.absolutePath)

        assertTrue(result.isSuccess)
        assertEquals(StorageTransferPreflightFailureReason.NONE, result.failureReason)
        source.delete()
        target.delete()
    }

    @Test
    fun `verifyTransferIntegrity fails for checksum mismatch`() {
        val source = createTempFileWithContent("chapter-one")
        val target = createTempFileWithContent("chapter-two")
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

        val result = checker.verifyTransferIntegrity(source.absolutePath, target.absolutePath)

        assertFalse(result.isSuccess)
        assertEquals(StorageTransferPreflightFailureReason.CHECKSUM_MISMATCH, result.failureReason)
        source.delete()
        target.delete()
    }

    @Test
    fun `verifyTransferIntegrity fails when source file missing`() {
        val target = createTempFileWithContent("chapter-two")
        val checker = ExternalStoragePreflightChecker(hasFullStoragePermission = { true })

        val result = checker.verifyTransferIntegrity("/tmp/does-not-exist-source.mp3", target.absolutePath)

        assertFalse(result.isSuccess)
        assertEquals(StorageTransferPreflightFailureReason.SOURCE_MISSING, result.failureReason)
        target.delete()
    }

    private fun createTempFileWithContent(content: String): File {
        val file = Files.createTempFile("jabook-transfer", ".tmp").toFile()
        file.writeText(content)
        return file
    }
}
