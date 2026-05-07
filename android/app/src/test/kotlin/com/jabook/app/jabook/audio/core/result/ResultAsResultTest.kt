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

package com.jabook.app.jabook.audio.core.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultAsResultTest {
    @Test
    fun `asResult emits loading then success`() =
        runTest {
            val values = flowOf(42).asResult().toList()

            assertEquals(2, values.size)
            assertEquals(Result.Loading, values[0])
            assertEquals(Result.Success(42), values[1])
        }

    @Test
    fun `asResult preserves upstream success emission order after loading`() =
        runTest {
            val values = flowOf(1, 2, 3).asResult().toList()

            assertEquals(4, values.size)
            assertEquals(Result.Loading, values[0])
            assertEquals(Result.Success(1), values[1])
            assertEquals(Result.Success(2), values[2])
            assertEquals(Result.Success(3), values[3])
        }

    @Test
    fun `asResult emits loading then error for non-cancellation exception`() =
        runTest {
            val values =
                flow<Int> {
                    throw IllegalStateException("boom")
                }.asResult().toList()

            assertEquals(2, values.size)
            assertEquals(Result.Loading, values[0])
            assertTrue(values[1] is Result.Error)
            assertEquals("boom", (values[1] as Result.Error).exception.message)
        }

    @Test(expected = CancellationException::class)
    fun `asResult rethrows cancellation exception`() =
        runTest {
            flow<Int> {
                throw CancellationException("cancel")
            }.asResult().toList()
        }
}
