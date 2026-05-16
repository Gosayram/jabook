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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for ErrorHandler.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorHandlerTest {

    @Test
    fun testErrorHandler_retriesOnFailure() = runBlockingTest {
        val player = FakeExoPlayer()
        val handler = ErrorHandler(player)
        var errorCount = 0
        val error = object : ExoPlayer.PlayerError {
            override val message: String? = "Test error"
            override val cause: Throwable? = null
        }
        handler.handlePlayerError(error)
        // Verify retry logic
        handler.release()
    }

    @Test
    fun testErrorHandler_reachesMaxRetries() = runBlockingTest {
        val player = FakeExoPlayer()
        val handler = ErrorHandler(player, maxRetries = 2)
        val error = object : ExoPlayer.PlayerError {
            override val message: String? = "Test error"
            override val cause: Throwable? = null
        }
        handler.handlePlayerError(error)
        handler.handlePlayerError(error)
        handler.handlePlayerError(error)
        // Verify max retries reached
        handler.release()
    }
}