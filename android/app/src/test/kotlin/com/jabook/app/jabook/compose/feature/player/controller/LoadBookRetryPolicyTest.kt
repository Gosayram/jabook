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

package com.jabook.app.jabook.compose.feature.player.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadBookRetryPolicyTest {
    @Test
    fun `allows retries only in configured range`() {
        assertTrue(LoadBookRetryPolicy.shouldRetry(1))
        assertTrue(LoadBookRetryPolicy.shouldRetry(2))
        assertTrue(LoadBookRetryPolicy.shouldRetry(3))
        assertFalse(LoadBookRetryPolicy.shouldRetry(0))
        assertFalse(LoadBookRetryPolicy.shouldRetry(4))
    }

    @Test
    fun `retry delay grows linearly with attempts`() {
        assertEquals(1_200L, LoadBookRetryPolicy.retryDelayMs(1))
        assertEquals(2_400L, LoadBookRetryPolicy.retryDelayMs(2))
        assertEquals(3_600L, LoadBookRetryPolicy.retryDelayMs(3))
    }

    @Test
    fun `retry delay clamps to first attempt for invalid numbers`() {
        assertEquals(1_200L, LoadBookRetryPolicy.retryDelayMs(0))
        assertEquals(1_200L, LoadBookRetryPolicy.retryDelayMs(-5))
    }
}
