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

package com.jabook.app.jabook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalMemoryTrimHandlerTest {
    @Test
    fun `does not clear heap caches for non-critical memory pressure`() {
        var cacheClearCount = 0
        val handler = CriticalMemoryTrimHandler { cacheClearCount++ }

        val handled = handler.onTrimMemory(TRIM_LEVEL_RUNNING_LOW)

        assertFalse(handled)
        assertEquals(0, cacheClearCount)
    }

    @Test
    fun `clears heap caches at running critical memory pressure`() {
        var cacheClearCount = 0
        val handler = CriticalMemoryTrimHandler { cacheClearCount++ }

        val handled = handler.onTrimMemory(TRIM_LEVEL_RUNNING_CRITICAL)

        assertTrue(handled)
        assertEquals(1, cacheClearCount)
    }

    @Test
    fun `clears heap caches when app is no longer foreground`() {
        var cacheClearCount = 0
        val handler = CriticalMemoryTrimHandler { cacheClearCount++ }

        val handled = handler.onTrimMemory(TRIM_LEVEL_COMPLETE)

        assertTrue(handled)
        assertEquals(1, cacheClearCount)
    }

    private companion object {
        const val TRIM_LEVEL_RUNNING_LOW = 10
        const val TRIM_LEVEL_RUNNING_CRITICAL = 15
        const val TRIM_LEVEL_COMPLETE = 80
    }
}
