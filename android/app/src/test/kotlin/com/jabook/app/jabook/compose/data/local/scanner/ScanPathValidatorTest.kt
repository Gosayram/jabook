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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ScanPathValidatorTest {
    @Test
    fun `normalize strips trailing slashes and surrounding whitespace`() {
        assertEquals("/storage/Books", ScanPathValidator.normalize("/storage/Books/"))
        assertEquals("/storage/Books", ScanPathValidator.normalize("  /storage/Books//  "))
        assertEquals("/storage/Books", ScanPathValidator.normalize("\t/storage/Books/ \n"))
        assertEquals("/storage/Books", ScanPathValidator.normalize("/storage/Books"))
        // Filesystem root must survive normalization
        assertEquals("/", ScanPathValidator.normalize("/"))
        assertEquals("/", ScanPathValidator.normalize(" / "))
    }

    @Test
    fun `classify detects content uri missing and not-directory paths`() {
        val existingDir = Files.createTempDirectory("jabook-validator").toString()
        val existingFile = Files.createTempFile("jabook-validator", ".txt").toString()

        assertEquals(ScanPathStatus.VALID, ScanPathValidator.classify(existingDir))
        assertEquals(ScanPathStatus.NOT_DIRECTORY, ScanPathValidator.classify(existingFile))
        assertEquals(ScanPathStatus.MISSING, ScanPathValidator.classify("/definitely/not/a/real/dir/jabook"))
        assertEquals(
            ScanPathStatus.UNSUPPORTED_URI,
            ScanPathValidator.classify("content://com.android.externalstorage.documents/tree/primary%3AAudiobooks"),
        )
    }

    @Test
    fun `disc-like directory names are detected for multi-disc merge`() {
        assertTrue(ScanPathValidator.isDiscDirectory("CD1"))
        assertTrue(ScanPathValidator.isDiscDirectory("cd 2"))
        assertTrue(ScanPathValidator.isDiscDirectory("Disc_3"))
        assertTrue(ScanPathValidator.isDiscDirectory("Part 4 - The Return"))
        assertTrue(ScanPathValidator.isDiscDirectory("dvd-05"))
        assertTrue(ScanPathValidator.isDiscDirectory("Side 1"))

        assertFalse(ScanPathValidator.isDiscDirectory("Book4"))
        assertFalse(ScanPathValidator.isDiscDirectory("CD"))
        assertFalse(ScanPathValidator.isDiscDirectory("Partners"))
        assertFalse(ScanPathValidator.isDiscDirectory("2001 A Space Odyssey"))
    }
}
