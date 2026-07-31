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

package com.jabook.app.jabook.indexing

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DelayedStopCoordinatorTest {
    @Test
    fun `cancel prevents a previous indexing run from stopping the next one`() =
        runTest {
            var stopCalls = 0
            val coordinator =
                DelayedStopCoordinator(
                    scope = backgroundScope,
                    delayMillis = 5_000L,
                    onStop = { stopCalls++ },
                )

            coordinator.schedule()
            advanceTimeBy(2_500L)
            coordinator.cancel()

            advanceTimeBy(2_500L)
            runCurrent()

            assertEquals(0, stopCalls)
        }

    @Test
    fun `rescheduling retains only the latest delayed stop`() =
        runTest {
            var stopCalls = 0
            val coordinator =
                DelayedStopCoordinator(
                    scope = backgroundScope,
                    delayMillis = 5_000L,
                    onStop = { stopCalls++ },
                )

            coordinator.schedule()
            advanceTimeBy(2_500L)
            coordinator.schedule()

            advanceTimeBy(2_500L)
            runCurrent()
            assertEquals(0, stopCalls)

            advanceTimeBy(2_500L)
            runCurrent()
            assertEquals(1, stopCalls)
        }
}
