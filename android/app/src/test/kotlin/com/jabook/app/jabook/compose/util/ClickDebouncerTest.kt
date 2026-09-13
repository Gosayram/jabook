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

package com.jabook.app.jabook.compose.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClickDebouncerTest {
    @Test
    fun rapidClicks_executeOnlyTheLast() =
        runTest(StandardTestDispatcher()) {
            val sut = ClickDebouncer(this, debounceTimeMs = 100)
            var invocations = 0
            repeat(5) {
                sut.debounce { invocations++ }
            }
            advanceTimeBy(200)
            advanceUntilIdle()
            assertEquals(1, invocations)
        }

    @Test
    fun spacedClicks_allExecute() =
        runTest(StandardTestDispatcher()) {
            val sut = ClickDebouncer(this, debounceTimeMs = 50)
            var invocations = 0
            sut.debounce { invocations++ }
            advanceTimeBy(100)
            advanceUntilIdle()
            sut.debounce { invocations++ }
            advanceTimeBy(100)
            advanceUntilIdle()
            assertEquals(2, invocations)
        }
}
