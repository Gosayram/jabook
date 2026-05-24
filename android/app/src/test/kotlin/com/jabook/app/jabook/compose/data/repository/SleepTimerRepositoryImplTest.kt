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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import org.junit.Test
import kotlin.test.assertEquals

class SleepTimerRepositoryImplTest {
    @Test
    fun `computeActiveState carries forward initialSeconds when remaining decreases`() {
        val previous = SleepTimerState.Active(remainingSeconds = 500, initialSeconds = 600)
        val result = SleepTimerRepositoryImpl.computeActiveState(previous, remaining = 400)
        assertEquals(600, result.initialSeconds)
        assertEquals(400, result.remainingSeconds)
    }

    @Test
    fun `computeActiveState resets initialSeconds when remaining increases`() {
        val previous = SleepTimerState.Active(remainingSeconds = 300, initialSeconds = 600)
        val result = SleepTimerRepositoryImpl.computeActiveState(previous, remaining = 900)
        assertEquals(900, result.initialSeconds)
        assertEquals(900, result.remainingSeconds)
    }

    @Test
    fun `computeActiveState eagerly emits Active with initialSeconds equal to remaining for fresh timer`() {
        val result = SleepTimerRepositoryImpl.computeActiveState(SleepTimerState.Idle, remaining = 300)
        assertEquals(300, result.initialSeconds)
        assertEquals(300, result.remainingSeconds)
    }

    @Test
    fun `computeActiveState carries forward initialSeconds when remaining equals previous initialSeconds`() {
        val previous = SleepTimerState.Active(remainingSeconds = 600, initialSeconds = 600)
        val result = SleepTimerRepositoryImpl.computeActiveState(previous, remaining = 600)
        assertEquals(600, result.initialSeconds)
        assertEquals(600, result.remainingSeconds)
    }
}
