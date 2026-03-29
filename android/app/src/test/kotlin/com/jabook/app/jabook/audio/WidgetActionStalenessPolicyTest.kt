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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetActionStalenessPolicyTest {
    @Test
    fun `does not ignore action when timestamp is missing`() {
        assertFalse(
            WidgetActionStalenessPolicy.shouldIgnore(
                actionCreatedAtMs = 0L,
                nowMs = 10_000L,
            ),
        )
    }

    @Test
    fun `does not ignore action when clock skew produces negative age`() {
        assertFalse(
            WidgetActionStalenessPolicy.shouldIgnore(
                actionCreatedAtMs = 12_000L,
                nowMs = 11_000L,
            ),
        )
    }

    @Test
    fun `ignores action older than max age`() {
        val now = WidgetActionStalenessPolicy.MAX_ACTION_AGE_MS + 50_000L
        assertTrue(
            WidgetActionStalenessPolicy.shouldIgnore(
                actionCreatedAtMs = now - WidgetActionStalenessPolicy.MAX_ACTION_AGE_MS - 1L,
                nowMs = now,
            ),
        )
    }
}
