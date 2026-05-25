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

package com.jabook.app.jabook.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePlaylistMemoryOptimizerTest {
    // --- calculateBufferWindow returns expected values ---

    @Test
    fun `low memory returns minimum window`() {
        val optimizer = createOptimizer(lowMemory = true, availMb = 50)
        assertEquals(1, optimizer.calculateBufferWindow())
    }

    @Test
    fun `very low available memory returns 2`() {
        val optimizer = createOptimizer(lowMemory = false, availMb = 200)
        assertEquals(2, optimizer.calculateBufferWindow())
    }

    @Test
    fun `low available memory returns 5`() {
        val optimizer = createOptimizer(lowMemory = false, availMb = 400)
        assertEquals(5, optimizer.calculateBufferWindow())
    }

    @Test
    fun `medium available memory returns 8`() {
        val optimizer = createOptimizer(lowMemory = false, availMb = 800)
        assertEquals(8, optimizer.calculateBufferWindow())
    }

    @Test
    fun `plenty of memory returns 10`() {
        val optimizer = createOptimizer(lowMemory = false, availMb = 4000)
        assertEquals(10, optimizer.calculateBufferWindow())
    }

    // --- isLowMemory ---

    @Test
    fun `isLowMemory returns true when system reports low`() {
        val optimizer = createOptimizer(lowMemory = true, availMb = 50)
        assertEquals(true, optimizer.isLowMemory())
    }

    @Test
    fun `isLowMemory returns false when plenty of memory`() {
        val optimizer = createOptimizer(lowMemory = false, availMb = 4000)
        assertEquals(false, optimizer.isLowMemory())
    }

    private fun createOptimizer(
        lowMemory: Boolean,
        availMb: Int,
    ): AdaptivePlaylistMemoryOptimizer {
        val memInfo =
            android.app.ActivityManager.MemoryInfo().apply {
                this.lowMemory = lowMemory
                availMem = availMb.toLong() * 1024 * 1024
            }
        val activityManager = org.mockito.kotlin.mock<android.app.ActivityManager>()
        org.mockito.kotlin.whenever(activityManager.getMemoryInfo(org.mockito.kotlin.any())).thenAnswer {
            (it.arguments[0] as android.app.ActivityManager.MemoryInfo).apply {
                this.lowMemory = memInfo.lowMemory
                this.availMem = memInfo.availMem
            }
        }
        return AdaptivePlaylistMemoryOptimizer(activityManager)
    }
}
