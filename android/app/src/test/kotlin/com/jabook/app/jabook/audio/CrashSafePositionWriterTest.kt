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

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
class CrashSafePositionWriterTest {
    private lateinit var repository: PlaybackPositionRepository

    @Before
    fun setUp() {
        repository = mock()
    }

    private fun createWriter(): CrashSafePositionWriter = CrashSafePositionWriter(repository)

    private suspend fun mockSaveSuccess() {
        whenever(repository.savePosition(any(), any(), any())).thenReturn(Result.Success(Unit))
    }

    private suspend fun mockSaveError() {
        whenever(repository.savePosition(any(), any(), any())).thenReturn(Result.Error(RuntimeException("fail")))
    }

    private suspend fun mockSaveThrow() {
        whenever(repository.savePosition(any(), any(), any())).thenThrow(RuntimeException("db error"))
    }

    // --- Successful write ---

    @Test
    fun `writePositionSync returns true on successful save`() =
        runBlocking {
            mockSaveSuccess()

            val writer = createWriter()
            val result = writer.writePositionSync("book-1", 3, 42_000L)
            assertTrue(result)
        }

    // --- Blank bookId ---

    @Test
    fun `writePositionSync returns false for blank bookId`() {
        val writer = createWriter()
        assertFalse(writer.writePositionSync("", 0, 10_000L))
    }

    @Test
    fun `writePositionSync returns false for whitespace bookId`() {
        val writer = createWriter()
        assertFalse(writer.writePositionSync("   ", 0, 10_000L))
    }

    // --- Negative position ---

    @Test
    fun `writePositionSync returns false for negative position`() {
        val writer = createWriter()
        assertFalse(writer.writePositionSync("book-1", 0, -1L))
    }

    // --- Repository error ---

    @Test
    fun `writePositionSync returns false when repository throws`() =
        runBlocking {
            mockSaveThrow()

            val writer = createWriter()
            val result = writer.writePositionSync("book-1", 0, 10_000L)
            assertFalse(result)
        }

    // --- Zero position is valid ---

    @Test
    fun `writePositionSync accepts zero position`() =
        runBlocking {
            mockSaveSuccess()

            val writer = createWriter()
            val result = writer.writePositionSync("book-1", 0, 0L)
            assertTrue(result)
        }

    // --- Repository error result ---

    @Test
    fun `writePositionSync returns false when repository returns error result`() =
        runBlocking {
            mockSaveError()

            val writer = createWriter()
            val result = writer.writePositionSync("book-1", 0, 10_000L)
            assertFalse(result)
        }

    // --- Negative trackIndex ---

    @Test
    fun `writePositionSync returns false for negative trackIndex`() {
        val writer = createWriter()
        assertFalse(writer.writePositionSync("book-1", -1, 10_000L))
    }
}
