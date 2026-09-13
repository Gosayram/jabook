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

package com.jabook.app.jabook.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileUtilsSanitizeFilenameTest {
    @Test
    fun `replaces volume-illegal characters`() {
        assertEquals("a_b_c_d", FileUtils.sanitizeFilename("a/b?c:d"))
        assertEquals("star_pipes", FileUtils.sanitizeFilename("star|pipes"))
    }

    @Test
    fun `strips control characters and trailing dots and spaces`() {
        assertEquals("clean", FileUtils.sanitizeFilename("cl\u0007ean.. "))
    }

    @Test
    fun `neutralizes windows reserved names case-insensitively`() {
        assertEquals("_", FileUtils.sanitizeFilename("CON"))
        assertEquals("_", FileUtils.sanitizeFilename("lpt1.txt"))
        assertEquals("constants", FileUtils.sanitizeFilename("constants"))
    }

    @Test
    fun `blank input gets fallback`() {
        assertEquals("untitled", FileUtils.sanitizeFilename("   "))
        assertEquals("untitled", FileUtils.sanitizeFilename("..."))
    }

    @Test
    fun `caps length without trailing dots`() {
        val out = FileUtils.sanitizeFilename("x".repeat(300) + "..", maxLength = 255)
        assertFalse(out.length > 255)
        assertFalse(out.endsWith("."))
    }
}
