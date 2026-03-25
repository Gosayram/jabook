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

package com.jabook.app.jabook.compose.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jabook.app.jabook.audio.core.result.Result as AudioResult
import com.jabook.app.jabook.compose.domain.model.Result as TypedResult

class ResultMappersTest {
    @Test
    fun `toTypedResult maps audio success to typed success`() {
        val source: AudioResult<Int> = AudioResult.Success(42)

        val mapped = source.toTypedResult()

        assertTrue(mapped is TypedResult.Success)
        assertEquals(42, (mapped as TypedResult.Success).data)
    }

    @Test
    fun `toTypedResult maps audio error to typed unknown with cause`() {
        val exception = IllegalStateException("audio-failure")
        val source: AudioResult<Int> = AudioResult.Error(exception)

        val mapped = source.toTypedResult()

        assertTrue(mapped is TypedResult.Error)
        val error = (mapped as TypedResult.Error).error
        assertTrue(error is AppError.Unknown)
        assertEquals("audio-failure", error.message)
        assertEquals(exception, error.cause)
    }

    @Test
    fun `toTypedResult maps loading to typed loading`() {
        val source: AudioResult<Int> = AudioResult.Loading

        val mapped = source.toTypedResult()

        assertTrue(mapped is TypedResult.Loading)
    }

    @Test
    fun `toAudioResult maps typed success to audio success`() {
        val source: TypedResult<Int, AppError> = TypedResult.Success(7)

        val mapped = source.toAudioResult()

        assertTrue(mapped is AudioResult.Success)
        assertEquals(7, (mapped as AudioResult.Success).data)
    }

    @Test
    fun `toAudioResult maps typed error with cause to audio error with same exception`() {
        val cause = IllegalArgumentException("bad-data")
        val source: TypedResult<Int, AppError> = TypedResult.Error(AppError.DataError.Generic("mapping-failed", cause))

        val mapped = source.toAudioResult()

        assertTrue(mapped is AudioResult.Error)
        assertEquals(cause, (mapped as AudioResult.Error).exception)
    }

    @Test
    fun `toAudioResult maps typed error without cause to runtime exception`() {
        val source: TypedResult<Int, AppError> = TypedResult.Error(AppError.AuthError.Unauthorized)

        val mapped = source.toAudioResult()

        assertTrue(mapped is AudioResult.Error)
        val exception = (mapped as AudioResult.Error).exception
        assertTrue(exception is RuntimeException)
        assertEquals(AppError.AuthError.Unauthorized.message, exception.message)
        assertNotNull(exception)
    }

    @Test
    fun `toAudioResult maps typed loading to audio loading`() {
        val source: TypedResult<Int, AppError> = TypedResult.Loading()

        val mapped = source.toAudioResult()

        assertTrue(mapped is AudioResult.Loading)
    }
}
